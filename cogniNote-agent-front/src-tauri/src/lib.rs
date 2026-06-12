use std::{
    fs::{self, OpenOptions},
    io::{Read, Write},
    net::{TcpListener, TcpStream},
    path::{Path, PathBuf},
    process::{Child, Command, Stdio},
    sync::Mutex,
    thread,
    time::{Duration, Instant},
};

#[cfg(windows)]
use std::os::windows::process::CommandExt;

use tauri::{path::BaseDirectory, App, AppHandle, Manager, WebviewUrl, WebviewWindowBuilder};
use tauri_plugin_dialog::{DialogExt, MessageDialogKind};

const APP_NAME: &str = "CogniNote";
const DESKTOP_VERSION: &str = env!("CARGO_PKG_VERSION");
#[cfg(target_os = "macos")]
const APP_IDENTIFIER: &str = "com.itqianchen.cogninote";
#[cfg(target_os = "macos")]
const MACOS_WEBVIEW_DATA_STORE_NAMESPACE: [u8; 16] = [
    0x6b, 0xb0, 0x86, 0x8c, 0x8e, 0xb5, 0x49, 0xc8, 0xad, 0x8b, 0x25, 0xc8, 0x6c, 0x25, 0x40, 0x13,
];
#[cfg(windows)]
const BACKEND_RESOURCE_DIR: &str = "backend/CogniNoteBackend";
#[cfg(target_os = "macos")]
const BACKEND_RESOURCE_DIR: &str = "backend/CogniNoteBackend.app";
#[cfg(not(any(windows, target_os = "macos")))]
const BACKEND_RESOURCE_DIR: &str = "backend/CogniNoteBackend";
#[cfg(windows)]
const BACKEND_EXE_NAME: &str = "CogniNoteBackend.exe";
#[cfg(target_os = "macos")]
const BACKEND_EXE_NAME: &str = "Contents/MacOS/CogniNoteBackend";
#[cfg(not(any(windows, target_os = "macos")))]
const BACKEND_EXE_NAME: &str = "CogniNoteBackend";
const MIN_PORT: u16 = 18080;
const MAX_PORT: u16 = 18120;
const STARTUP_TIMEOUT: Duration = Duration::from_secs(45);
const HEALTH_CHECK_INTERVAL: Duration = Duration::from_millis(500);
#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x0800_0000;

struct BackendProcess {
    child: Mutex<Option<Child>>,
    port: u16,
    log_path: PathBuf,
}

impl Drop for BackendProcess {
    fn drop(&mut self) {
        if let Ok(mut child_guard) = self.child.lock() {
            if let Some(mut child) = child_guard.take() {
                // 桌面窗口退出时必须同步收掉 jpackage 后端，避免端口和 SQLite 文件锁残留。
                let _ = child.kill();
                let _ = child.wait();
            }
        }
    }
}

pub fn run() {
    let app = tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| {
            #[cfg(target_os = "macos")]
            app.dialog()
                .message("CogniNote 已经在运行。升级或降级后，请先完全退出旧版本，再从 /Applications 打开新版本。")
                .kind(MessageDialogKind::Info)
                .title("CogniNote 已在运行")
                .blocking_show();

            if let Some(window) = app.get_webview_window("main") {
                let _ = window.unminimize();
                let _ = window.show();
                let _ = window.set_focus();
            }
        }))
        .invoke_handler(tauri::generate_handler![pick_knowledge_folder])
        .setup(setup_desktop)
        .on_window_event(|window, event| {
            if matches!(event, tauri::WindowEvent::CloseRequested { .. }) {
                shutdown_backend(&window.app_handle());
            }
        })
        .build(tauri::generate_context!())
        .expect("failed to run CogniNote desktop shell");

    app.run(|app_handle, event| {
        if let tauri::RunEvent::ExitRequested { .. } = event {
            shutdown_backend(app_handle);
        }
    });
}

#[tauri::command]
fn pick_knowledge_folder(app: AppHandle) -> Option<String> {
    app.dialog()
        .file()
        .blocking_pick_folder()
        .map(|path| path.to_string())
}

fn setup_desktop(app: &mut App) -> Result<(), Box<dyn std::error::Error>> {
    match start_backend_and_open_window(app) {
        Ok(()) => Ok(()),
        Err(error) => {
            show_startup_error(app.handle(), &error);
            Err(std::io::Error::new(std::io::ErrorKind::Other, error).into())
        }
    }
}

fn start_backend_and_open_window(app: &mut App) -> Result<(), String> {
    let port = select_available_port()?;
    let backend_exe = resolve_backend_exe(app)?;
    let log_path = prepare_backend_log_path()?;
    append_desktop_startup_log(app, &backend_exe, port, &log_path);
    let reset_webview_cache = prepare_webview_cache_for_current_version(&log_path);
    let child = spawn_backend(&backend_exe, port, &log_path)?;

    if let Err(error) = wait_until_backend_ready(port) {
        let mut child = child;
        let _ = child.kill();
        let _ = child.wait();
        return Err(error);
    }

    // Tauri 配置里不预创建窗口，避免后端未就绪时显示一个不可用的空白页面。
    // 健康检查通过后再把 WebView 指到 Spring Boot 同源页面，前端现有 /api 相对路径即可继续工作。
    if let Err(error) = open_main_window(app.handle(), port, reset_webview_cache, &log_path) {
        let mut child = child;
        let _ = child.kill();
        let _ = child.wait();
        return Err(error);
    }
    app.manage(BackendProcess {
        child: Mutex::new(Some(child)),
        port,
        log_path,
    });
    Ok(())
}

fn select_available_port() -> Result<u16, String> {
    // README 和后端默认端口都围绕 18080，桌面版只在小范围内漂移，便于日志和防火墙排查。
    for port in MIN_PORT..=MAX_PORT {
        if TcpListener::bind(("127.0.0.1", port)).is_ok() {
            return Ok(port);
        }
    }
    Err(format!(
        "没有找到可用端口，请释放 {}-{} 之间的本地端口后重试。",
        MIN_PORT, MAX_PORT
    ))
}

fn resolve_backend_exe(app: &App) -> Result<PathBuf, String> {
    // jpackage app-image 不是单文件程序，启动器旁边的 app/runtime 目录缺一不可。
    // Windows 与 macOS 分别打包自己的完整 app-image，Tauri 只从当前平台资源目录定位启动器。
    let resource_dir = app
        .path()
        .resolve(BACKEND_RESOURCE_DIR, BaseDirectory::Resource)
        .map_err(|error| format!("无法定位后端资源目录：{error}"))?;
    let exe = resource_dir.join(BACKEND_EXE_NAME);
    if exe.exists() {
        Ok(exe)
    } else {
        Err(format!(
            "未找到后端启动器：{}\n请先运行当前平台的桌面后端打包脚本生成 jpackage app-image。",
            exe.display()
        ))
    }
}

fn prepare_backend_log_path() -> Result<PathBuf, String> {
    let log_dir = app_support_dir()?.join("logs");
    fs::create_dir_all(&log_dir)
        .map_err(|error| format!("无法创建日志目录 {}：{error}", log_dir.display()))?;
    Ok(log_dir.join("desktop-backend.log"))
}

#[cfg(target_os = "macos")]
fn prepare_webview_cache_for_current_version(log_path: &Path) -> bool {
    let marker_path = match app_support_dir() {
        Ok(path) => path.join("desktop-webview-version.txt"),
        Err(error) => {
            append_log_line(
                log_path,
                &format!("macOS WebView cache marker unavailable: {error}"),
            );
            return true;
        }
    };
    let previous_version = fs::read_to_string(&marker_path)
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty());

    if previous_version.as_deref() == Some(DESKTOP_VERSION) {
        return false;
    }

    // macOS WKWebView 会跨 app 覆盖保留缓存；版本变化时清一次，避免升级后仍加载旧前端资源。
    append_log_line(
        log_path,
        &format!(
            "macOS WebView cache reset requested: previousVersion={} currentVersion={}",
            previous_version.as_deref().unwrap_or("<missing>"),
            DESKTOP_VERSION
        ),
    );
    remove_macos_webview_cache_dirs(log_path);

    if let Some(parent) = marker_path.parent() {
        if let Err(error) = fs::create_dir_all(parent) {
            append_log_line(
                log_path,
                &format!(
                    "Unable to create WebView version marker directory {}: {error}",
                    parent.display()
                ),
            );
            return true;
        }
    }
    if let Err(error) = fs::write(&marker_path, DESKTOP_VERSION) {
        append_log_line(
            log_path,
            &format!(
                "Unable to write WebView version marker {}: {error}",
                marker_path.display()
            ),
        );
    }
    true
}

#[cfg(not(target_os = "macos"))]
fn prepare_webview_cache_for_current_version(_log_path: &Path) -> bool {
    false
}

#[cfg(target_os = "macos")]
fn remove_macos_webview_cache_dirs(log_path: &Path) {
    let Some(home) = std::env::var_os("HOME").map(PathBuf::from) else {
        append_log_line(
            log_path,
            "Unable to clean macOS WebView cache: HOME is unavailable",
        );
        return;
    };
    let library_dir = home.join("Library");
    let webkit_data_dir = library_dir
        .join("WebKit")
        .join(APP_IDENTIFIER)
        .join("WebsiteData");
    let container_library_dir = library_dir
        .join("Containers")
        .join(APP_IDENTIFIER)
        .join("Data")
        .join("Library");
    let container_webkit_data_dir = container_library_dir
        .join("WebKit")
        .join(APP_IDENTIFIER)
        .join("WebsiteData");
    let cache_dirs = [
        library_dir.join("Caches").join(APP_IDENTIFIER),
        library_dir.join("Caches").join(APP_NAME),
        webkit_data_dir.join("NetworkCache"),
        webkit_data_dir.join("CacheStorage"),
        webkit_data_dir.join("ServiceWorkers"),
        container_library_dir.join("Caches").join(APP_IDENTIFIER),
        container_library_dir.join("Caches").join(APP_NAME),
        container_library_dir.join("Caches").join("WebKit"),
        container_webkit_data_dir.join("NetworkCache"),
        container_webkit_data_dir.join("CacheStorage"),
        container_webkit_data_dir.join("ServiceWorkers"),
    ];

    for cache_dir in cache_dirs {
        remove_cache_path_under(&library_dir, &cache_dir, log_path);
    }
}

#[cfg(target_os = "macos")]
fn remove_cache_path_under(allowed_root: &Path, path: &Path, log_path: &Path) {
    if !path.exists() {
        return;
    }

    let allowed_root = match allowed_root.canonicalize() {
        Ok(root) => root,
        Err(error) => {
            append_log_line(
                log_path,
                &format!(
                    "Skipping WebView cache cleanup because {} cannot be resolved: {error}",
                    allowed_root.display()
                ),
            );
            return;
        }
    };
    let resolved_path = match path.canonicalize() {
        Ok(path) => path,
        Err(error) => {
            append_log_line(
                log_path,
                &format!(
                    "Skipping unresolved WebView cache path {}: {error}",
                    path.display()
                ),
            );
            return;
        }
    };

    if !resolved_path.starts_with(&allowed_root) {
        append_log_line(
            log_path,
            &format!(
                "Skipping WebView cache path outside ~/Library: {}",
                resolved_path.display()
            ),
        );
        return;
    }

    let result = if resolved_path.is_dir() {
        fs::remove_dir_all(&resolved_path)
    } else {
        fs::remove_file(&resolved_path)
    };
    match result {
        Ok(()) => append_log_line(
            log_path,
            &format!(
                "Removed macOS WebView cache path: {}",
                resolved_path.display()
            ),
        ),
        Err(error) => append_log_line(
            log_path,
            &format!(
                "Unable to remove macOS WebView cache path {}: {error}",
                resolved_path.display()
            ),
        ),
    }
}

fn app_support_dir() -> Result<PathBuf, String> {
    #[cfg(windows)]
    {
        let app_data = std::env::var_os("APPDATA")
            .map(PathBuf::from)
            .ok_or_else(|| "无法读取 APPDATA 环境变量，不能创建桌面启动日志。".to_string())?;
        return Ok(app_data.join(APP_NAME));
    }

    #[cfg(target_os = "macos")]
    {
        let home = std::env::var_os("HOME")
            .map(PathBuf::from)
            .ok_or_else(|| "无法读取 HOME 环境变量，不能创建 macOS 应用数据目录。".to_string())?;
        return Ok(home
            .join("Library")
            .join("Application Support")
            .join(APP_NAME));
    }

    #[cfg(not(any(windows, target_os = "macos")))]
    {
        let home = std::env::var_os("HOME")
            .map(PathBuf::from)
            .ok_or_else(|| "无法读取 HOME 环境变量，不能创建应用数据目录。".to_string())?;
        Ok(home.join(".cogninote"))
    }
}

fn spawn_backend(backend_exe: &Path, port: u16, log_path: &Path) -> Result<Child, String> {
    let stdout = OpenOptions::new()
        .create(true)
        .append(true)
        .open(log_path)
        .map_err(|error| format!("无法打开后端日志 {}：{error}", log_path.display()))?;
    let stderr = stdout
        .try_clone()
        .map_err(|error| format!("无法复用后端日志句柄：{error}"))?;

    append_log_line(
        log_path,
        &format!(
            "==== Starting backend executable: {} on port {} ====",
            backend_exe.display(),
            port
        ),
    );

    let mut command = Command::new(backend_exe);
    command
        .env("COGNINOTE_PORT", port.to_string())
        .env("COGNINOTE_DESKTOP", "true")
        .current_dir(backend_exe.parent().unwrap_or_else(|| Path::new(".")))
        .stdout(Stdio::from(stdout))
        .stderr(Stdio::from(stderr));
    configure_backend_process_window(&mut command);

    command
        .spawn()
        .map_err(|error| format!("无法启动后端进程：{error}"))
}

fn append_desktop_startup_log(app: &App, backend_exe: &Path, port: u16, log_path: &Path) {
    let package_info = app.package_info();
    let app_version = package_info.version.to_string();
    let resource_dir = backend_exe
        .parent()
        .map(|path| path.display().to_string())
        .unwrap_or_else(|| "-".to_string());
    append_log_line(
        log_path,
        &format!(
            "\n==== CogniNote desktop startup: desktopVersion={} packageVersion={} currentExe={} backendResourceDir={} selectedPort={} ====",
            DESKTOP_VERSION,
            app_version,
            std::env::current_exe()
                .map(|path| path.display().to_string())
                .unwrap_or_else(|_| "-".to_string()),
            resource_dir,
            port
        ),
    );
}

fn configure_backend_process_window(command: &mut Command) {
    // jpackage 生成的后端启动器在 Windows 下可能按控制台程序启动。
    // 桌面壳已经把输出重定向到日志文件，这里显式禁止子进程创建 cmd 窗口，避免用户双击主程序后看到后台服务窗口常驻。
    #[cfg(windows)]
    command.creation_flags(CREATE_NO_WINDOW);

    #[cfg(target_os = "macos")]
    configure_macos_backend_environment(command);
}

#[cfg(target_os = "macos")]
fn configure_macos_backend_environment(command: &mut Command) {
    if let Ok(app_dir) = app_support_dir() {
        command
            .env("COGNINOTE_DATA_DIR", &app_dir)
            .env("COGNINOTE_LOG_FILE", app_dir.join("logs").join("app.log"));
    }
}

fn wait_until_backend_ready(port: u16) -> Result<(), String> {
    let deadline = Instant::now() + STARTUP_TIMEOUT;
    while Instant::now() < deadline {
        if is_system_status_ready(port) {
            return Ok(());
        }
        thread::sleep(HEALTH_CHECK_INTERVAL);
    }
    Err(format!(
        "后端启动超时。请查看日志：{}",
        desktop_backend_log_hint()
    ))
}

fn desktop_backend_log_hint() -> String {
    #[cfg(windows)]
    {
        return format!("%APPDATA%\\{}\\logs\\desktop-backend.log", APP_NAME);
    }

    #[cfg(target_os = "macos")]
    {
        return format!(
            "~/Library/Application Support/{}/logs/desktop-backend.log",
            APP_NAME
        );
    }

    #[cfg(not(any(windows, target_os = "macos")))]
    {
        "~/.cogninote/logs/desktop-backend.log".to_string()
    }
}

fn is_system_status_ready(port: u16) -> bool {
    let Ok(mut stream) = TcpStream::connect(("127.0.0.1", port)) else {
        return false;
    };
    let _ = stream.set_read_timeout(Some(Duration::from_millis(800)));
    let _ = stream.set_write_timeout(Some(Duration::from_millis(800)));

    let request =
        b"GET /api/system/status HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n";
    if stream.write_all(request).is_err() {
        return false;
    }

    let mut response = String::new();
    stream.read_to_string(&mut response).is_ok() && response.starts_with("HTTP/1.1 200")
}

fn open_main_window(
    app: &AppHandle,
    port: u16,
    reset_webview_cache: bool,
    log_path: &Path,
) -> Result<(), String> {
    let url = format!("http://127.0.0.1:{port}/")
        .parse()
        .map_err(|error| format!("无法生成桌面窗口 URL：{error}"))?;
    let initial_url = initial_webview_url(&url, reset_webview_cache)?;
    let builder = WebviewWindowBuilder::new(app, "main", WebviewUrl::External(initial_url))
        .title(APP_NAME)
        .inner_size(1280.0, 820.0)
        .min_inner_size(960.0, 640.0)
        .center();

    #[cfg(target_os = "macos")]
    let builder = {
        // WKWebView does not support WebView2-style data_directory on macOS.
        // A version-scoped store prevents an older desktop release from reusing
        // stale HTTP/Vite caches while business data stays in Application Support.
        let builder = builder.data_store_identifier(macos_webview_data_store_identifier());
        if reset_webview_cache {
            builder.visible(false)
        } else {
            builder
        }
    };

    let window = builder
        .build()
        .map_err(|error| format!("无法创建桌面窗口：{error}"))?;

    #[cfg(not(target_os = "macos"))]
    {
        let _ = &window;
        let _ = log_path;
    }

    #[cfg(target_os = "macos")]
    if reset_webview_cache {
        match window.clear_all_browsing_data() {
            Ok(()) => append_log_line(log_path, "Requested macOS WKWebView browsing data cleanup"),
            Err(error) => append_log_line(
                log_path,
                &format!("Unable to request macOS WKWebView browsing data cleanup: {error}"),
            ),
        }
        window
            .navigate(url)
            .map_err(|error| format!("无法加载桌面页面：{error}"))?;
        let _ = window.show();
        let _ = window.set_focus();
    }
    Ok(())
}

fn initial_webview_url(
    target_url: &tauri::Url,
    _reset_webview_cache: bool,
) -> Result<tauri::Url, String> {
    #[cfg(target_os = "macos")]
    if _reset_webview_cache {
        return "about:blank"
            .parse()
            .map_err(|error| format!("无法生成 macOS WebView 初始 URL：{error}"));
    }

    Ok(target_url.clone())
}

#[cfg(target_os = "macos")]
fn macos_webview_data_store_identifier() -> [u8; 16] {
    let mut identifier = MACOS_WEBVIEW_DATA_STORE_NAMESPACE;
    for (index, byte) in DESKTOP_VERSION.as_bytes().iter().enumerate() {
        let slot = index % identifier.len();
        identifier[slot] = identifier[slot]
            .wrapping_mul(31)
            .wrapping_add(*byte)
            .rotate_left((index % 7) as u32);
    }
    identifier
}

fn shutdown_backend(app: &AppHandle) {
    if let Some(state) = app.try_state::<BackendProcess>() {
        if let Ok(mut child_guard) = state.child.lock() {
            if let Some(mut child) = child_guard.take() {
                append_log_line(
                    &state.log_path,
                    &format!("==== Stopping backend on port {} ====", state.port),
                );
                let _ = child.kill();
                let _ = child.wait();
            }
        }
    }
}

fn show_startup_error(app: &AppHandle, message: &str) {
    app.dialog()
        .message(message)
        .kind(MessageDialogKind::Error)
        .title("CogniNote 启动失败")
        .blocking_show();
}

fn append_log_line(log_path: &Path, line: &str) {
    if let Ok(mut file) = OpenOptions::new().create(true).append(true).open(log_path) {
        let _ = writeln!(file, "{line}");
    }
}

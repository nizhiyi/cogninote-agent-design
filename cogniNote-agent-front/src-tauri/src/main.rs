#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    // 桌面端入口只委托库层 run，命令注册和窗口插件集中在 lib.rs，便于测试和复用。
    cogninote_agent_lib::run()
}

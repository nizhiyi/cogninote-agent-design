package com.itqianchen.agentdesign.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 桌面前端静态资源的 HTTP 缓存策略。
 *
 * <p>WebView2 会跨应用版本保留缓存，入口页和静态 chunk 必须强制重新校验，避免旧前端壳加载新后端。</p>
 */
@Configuration
public class StaticResourceCacheConfig {

    private static final String CACHE_CONTROL_VALUE = "no-store, no-cache, max-age=0, must-revalidate";

    /**
     * 注册静态资源缓存控制过滤器。
     *
     * @return 过滤器注册 Bean
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> staticResourceCacheHeaderFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
            /**
             * 为前端资源响应追加禁用缓存头。
             *
             * @param request HTTP 请求
             * @param response HTTP 响应
             * @param filterChain Servlet 过滤器链
             * @throws ServletException 当后续过滤器或资源处理失败时抛出
             * @throws IOException 当响应写入失败时抛出
             */
            @Override
            protected void doFilterInternal(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    FilterChain filterChain
            ) throws ServletException, IOException {
                if (isFrontendResourceRequest(request)) {
                    /*
                     * Desktop WebView2 persists browser data across app upgrades.
                     * Force every local frontend entry and chunk to revalidate so
                     * a cached old SPA shell cannot mask newly installed binaries.
                     */
                    response.setHeader("Cache-Control", CACHE_CONTROL_VALUE);
                    response.setHeader("Pragma", "no-cache");
                    response.setDateHeader("Expires", 0);
                }
                filterChain.doFilter(request, response);
            }
        });
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * 判断请求是否是前端资源或 SPA 路由。
     *
     * @param request HTTP 请求
     * @return 是否需要禁用缓存
     */
    private static boolean isFrontendResourceRequest(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return false;
        }

        String path = request.getRequestURI();
        if (path.startsWith("/api/")) {
            return false;
        }
        return path.equals("/")
                || path.equals("/index.html")
                || path.equals("/favicon.ico")
                || path.startsWith("/assets/")
                || path.equals("/chat")
                || path.equals("/knowledge")
                || path.equals("/model-config")
                || path.equals("/settings");
    }
}

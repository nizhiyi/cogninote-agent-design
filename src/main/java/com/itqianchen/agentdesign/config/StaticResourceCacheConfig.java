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
 * Static Resource Cache 配置桌面前端资源的 HTTP 缓存策略。
 */
@Configuration
public class StaticResourceCacheConfig {

    private static final String CACHE_CONTROL_VALUE = "no-store, no-cache, max-age=0, must-revalidate";

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> staticResourceCacheHeaderFilter() {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new OncePerRequestFilter() {
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

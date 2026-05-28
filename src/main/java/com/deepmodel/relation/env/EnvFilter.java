package com.deepmodel.relation.env;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 从请求 Header X-Env 读取当前环境名并写入 {@link EnvContext}。
 * 支持降级：query 参数 ?env=xxx 也可（便于浏览器直接打开调试 URL）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class EnvFilter implements Filter {

    public static final String HEADER = "X-Env";
    public static final String QUERY_PARAM = "env";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest http) {
                String env = http.getHeader(HEADER);
                if (env == null || env.isBlank()) {
                    env = http.getParameter(QUERY_PARAM);
                }
                EnvContext.set(env);
            }
            chain.doFilter(request, response);
        } finally {
            EnvContext.clear();
        }
    }
}

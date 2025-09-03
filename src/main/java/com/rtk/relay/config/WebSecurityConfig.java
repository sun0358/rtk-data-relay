package com.rtk.relay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Web安全配置类
 * 禁用Spring Security认证，允许所有监控接口无需认证访问
 *
 * @author RTK Team
 * @version 1.0.0
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    /**
     * 配置安全过滤器链
     * 禁用所有安全认证，允许匿名访问所有接口
     *
     * @param http HTTP安全配置
     * @return 安全过滤器链
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF保护
            .csrf().disable()
            // 禁用session管理
            .sessionManagement().disable()
            // 禁用HTTP Basic认证
            .httpBasic().disable()
            // 禁用表单登录
            .formLogin().disable()
            // 禁用登出功能
            .logout().disable()
            // 允许所有请求无需认证
            .authorizeHttpRequests(authz -> authz
                .anyRequest().permitAll()
            );

        return http.build();
    }
}

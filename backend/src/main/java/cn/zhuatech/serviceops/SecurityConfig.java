// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
package cn.zhuatech.serviceops;

import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/** 会话认证与 CSRF 防护；前后端同源部署。知华科技 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。 */
@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService users(EntityManager em) {
    return name -> {
      var u =
          em
              .createQuery("from Account where username=:n", Models.Account.class)
              .setParameter("n", name)
              .getResultList()
              .stream()
              .findFirst()
              .orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
      return new LoginPrincipal(u);
    };
  }

  /** 登录不重定向，不把会话令牌交给脚本；所有写请求保留 CSRF 检查。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
  @Bean
  SecurityFilterChain security(HttpSecurity h) throws Exception {
    h.authorizeHttpRequests(
            a ->
                a.requestMatchers("/api/auth/csrf", "/api/auth/login", "/actuator/health", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(
            f ->
                f.loginProcessingUrl("/api/auth/login")
                    .successHandler(
                        (q, s, a) -> {
                          s.setContentType("application/json;charset=UTF-8");
                          s.getWriter().write("{\"ok\":true}");
                        })
                    .failureHandler(
                        (q, s, e) -> {
                          s.setStatus(401);
                          s.setContentType("application/json;charset=UTF-8");
                          s.getWriter().write("{\"message\":\"账号或密码错误\"}");
                        }))
        .logout(
            l ->
                l.logoutUrl("/api/auth/logout").logoutSuccessHandler((q, s, a) -> s.setStatus(204)))
        .exceptionHandling(
            e ->
                e.authenticationEntryPoint(
                        (q, s, x) -> {
                          s.setStatus(401);
                          s.setContentType("application/json;charset=UTF-8");
                          s.getWriter().write("{\"message\":\"请登录\"}");
                        })
                    .accessDeniedHandler(
                        (q, s, x) -> {
                          s.setStatus(403);
                          s.setContentType("application/json;charset=UTF-8");
                          s.getWriter().write("{\"message\":\"请求校验失败或没有权限，请刷新后重试\"}");
                        }));
    return h.build();
  }
}

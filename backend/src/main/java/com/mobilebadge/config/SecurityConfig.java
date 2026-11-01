package com.mobilebadge.config;

import com.mobilebadge.repo.UserAccountRepository;
import com.mobilebadge.security.ReloadUserFilter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http, UserAccountRepository users) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(request -> {
          CorsConfiguration cfg = new CorsConfiguration();
          cfg.setAllowedOrigins(List.of("http://localhost:43124", "http://127.0.0.1:43124"));
          cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
          cfg.setAllowedHeaders(List.of("*"));
          cfg.setAllowCredentials(true);
          return cfg;
        }))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .securityContext(context -> context.requireExplicitSave(false))
        .formLogin(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
            .requestMatchers("/api/**").authenticated()
            .anyRequest().permitAll())
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint((request, response, authException) -> {
              response.setStatus(401);
              response.setCharacterEncoding("UTF-8");
              response.setContentType("application/json");
              response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"未登录\"}");
            })
            .accessDeniedHandler((request, response, accessDeniedException) -> {
              response.setStatus(403);
              response.setCharacterEncoding("UTF-8");
              response.setContentType("application/json");
              response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"无权访问该页面\"}");
            }))
        .addFilterAfter(new ReloadUserFilter(users), SecurityContextHolderFilter.class);
    return http.build();
  }
}

package com.trimax.linkhub.config;

import com.trimax.linkhub.service.LinkHubService;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean AuthenticationManager authenticationManager(LinkHubService service, PasswordEncoder encoder) {
        UserDetailsService users = email -> service.findByEmail(email)
                .map(a -> User.withUsername(a.getId().toString()).password(a.getPasswordHash()).roles("USER").build())
                .orElseThrow(() -> new UsernameNotFoundException("Thông tin đăng nhập không đúng."));
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean SecurityFilterChain security(HttpSecurity http, SecurityContextRepository repository, com.trimax.linkhub.service.AccountSecurityService accounts,OperationsAccess operations,com.trimax.linkhub.service.AuditService audit) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/ops/**").access((authentication,context)->new org.springframework.security.authorization.AuthorizationDecision(operations.allowed(context.getRequest())))
                        .requestMatchers("/actuator/metrics", "/actuator/metrics/**", "/actuator/prometheus").access((authentication,context)->new org.springframework.security.authorization.AuthorizationDecision(operations.allowed(context.getRequest())))
                        .requestMatchers("/api/me", "/api/me/**").authenticated()
                        .requestMatchers("/api/auth/password-reset/**", "/api/auth/email-verification/confirm", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/login", "/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/app", "/ops/metrics", "/assets/**", "/favicon.ico", "/{username}").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .securityContext(context -> context.securityContextRepository(repository))
                .addFilterBefore(new AuditFilter(audit,accounts),org.springframework.security.web.csrf.CsrfFilter.class)
                .addFilterBefore(new AccountSessionFilter(accounts),org.springframework.security.web.access.intercept.AuthorizationFilter.class)
                .requestCache(cache -> cache.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> { res.setStatus(401); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Authentication required\"}"); })
                        .accessDeniedHandler((req, res, ex) -> { res.setStatus(403); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Access denied or invalid CSRF token\"}"); }))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'")))
                .logout(logout -> logout.logoutUrl("/api/auth/logout").logoutSuccessHandler((req, res, auth) -> res.setStatus(204)).deleteCookies("SESSION", "JSESSIONID"))
                .build();
    }
}

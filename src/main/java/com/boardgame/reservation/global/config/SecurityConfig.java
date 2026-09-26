package com.boardgame.reservation.global.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * 세션 기반 인증 설정.
 *
 * 로그인 흐름:
 *   POST /api/auth/login (JSON) → AuthController 에서 AuthenticationManager로 인증
 *   → SecurityContext를 HttpSession에 저장 → 응답에 JSESSIONID 쿠키
 *   → 이후 요청은 쿠키로 세션을 찾아 인증 상태 복원
 *
 * formLogin(폼 파라미터 방식) 대신 JSON 로그인 API를 직접 만든 이유:
 *   React(SPA)에서 fetch/axios로 JSON을 보내기 때문.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver
    ) throws Exception {
        http
                // SPA + JSON API: CSRF는 일단 비활성화.
                // 대신 세션 쿠키에 SameSite=Lax(application.yml)를 걸어 교차 사이트 POST를 막는다.
                // (프론트 붙일 때 CookieCsrfTokenRepository 적용 여부 다시 검토)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                // 로그인 컨트롤러에서 직접 저장하는 저장소와 같은 인스턴스를 사용
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository))

                .authorizeHttpRequests(auth -> auth
                        // 인증
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        // 조회는 누구나 (설계문서 3-2, 3-3)
                        .requestMatchers(HttpMethod.GET, "/api/boardgames/**", "/api/parties/**").permitAll()
                        // 업로드 이미지(아바타 등) 서빙. key 형식 검증은 FileStorage 가 한다
                        .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                        // 관리자 가입 승인은 SUPER_ADMIN 만 (ADMIN 은 403)
                        .requestMatchers("/api/admin/admin-requests/**").hasRole("SUPER_ADMIN")
                        // 보드게임 등록/수정/삭제는 ADMIN만
                        .requestMatchers("/api/boardgames/**").hasRole("ADMIN")
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())

                // 401/403을 GlobalExceptionHandler로 위임 → 응답 포맷 통일
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                exceptionResolver.resolveException(request, response, null, e))
                        .accessDeniedHandler((request, response, e) ->
                                exceptionResolver.resolveException(request, response, null, e)))

                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(HttpStatus.OK.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"success\":true,\"data\":null,\"message\":null}");
                        }));

        return http.build();
    }

    /**
     * 역할 계층: SUPER_ADMIN > ADMIN > USER.
     * hasRole('ADMIN') 로 보호된 API 에 SUPER_ADMIN 도 통과한다.
     */
    @Bean
    public static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_SUPER_ADMIN > ROLE_ADMIN
                ROLE_ADMIN > ROLE_USER
                """);
    }

    /** 로그인 성공 시 SecurityContext를 HttpSession에 저장하는 저장소 */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** UserDetailsService + PasswordEncoder 빈을 이용해 Spring Boot가 구성한 AuthenticationManager */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

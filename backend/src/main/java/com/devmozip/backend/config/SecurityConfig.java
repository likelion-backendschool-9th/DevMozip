package com.devmozip.backend.config;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.handler.JsonAccessDeniedHandler;
import com.devmozip.backend.security.handler.JsonAuthenticationEntryPoint;
import com.devmozip.backend.security.jwt.JwtAuthenticationFilter;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.SuspendedMemberStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;
    private final SuspendedMemberStore suspendedMemberStore;

    public SecurityConfig(JwtProvider jwtProvider,
                          JsonAuthenticationEntryPoint authenticationEntryPoint,
                          JsonAccessDeniedHandler accessDeniedHandler,
                          SuspendedMemberStore suspendedMemberStore) {
        this.jwtProvider = jwtProvider;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.suspendedMemberStore = suspendedMemberStore;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CSRF 비활성화
            .csrf(AbstractHttpConfigurer::disable)

            // 세션을 사용하지 않으므로 STATELESS로 설정
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 경로별 접근 권한 설정
            .authorizeHttpRequests(auth -> auth
                // 로그인, 회원가입 등 공개 API는 토큰 없이도 접근 허용
                .requestMatchers("/api/v1/auth/**").permitAll()
                // 오류 응답(404, 409 등)이 403으로 바뀌지 않도록 스프링의 오류 경로는 허용
                .requestMatchers("/error").permitAll()
                // 메신저 WebSocket은 쿠키로 인증하므로 Bearer 필터 대신 핸드셰이크 인터셉터가 검증한다
                .requestMatchers(HttpMethod.GET, "/ws/messages").permitAll()
                // 헬스체크는 토큰 없이 확인한다 (프론트 /api/health, 블루그린 배포 스크립트)
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me/reviews").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/{slug}/reviews").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me/posts").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/{slug}/posts").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me/liked-posts").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me/portfolios", "/api/v1/portfolios/{id}").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me/projects").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/{slug}/projects").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/{slug}/portfolios").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me/links").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/{slug}/links").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me/options").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/{slug}/options").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/me/introduction").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/{slug}/introduction").permitAll()
                // 팔로우 상태와 팔로워·팔로잉 목록은 공개한다. 팔로우·언팔로우·알림 설정은 아래 기본 규칙대로 로그인이 필요하다.
                .requestMatchers(HttpMethod.GET, "/api/v1/users/{slug}/follow", "/api/v1/users/{slug}/followers", "/api/v1/users/{slug}/following").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/users/{slug}", "/api/v1/files/{id}").permitAll()
                // 공개 프로젝트 조회만 허용한다. options·관리·생성은 기존 인증 정책을 유지한다.
                .requestMatchers(HttpMethod.GET, "/api/v1/projects", "/api/v1/projects/{id:[0-9]+}").permitAll()
                // SNS 피드 목록·상세·댓글 목록은 공개한다(docs/feed-plan.md 3절). 팔로잉 피드와 쓰기 요청은 아래 기본 규칙대로 로그인이 필요하다.
                .requestMatchers(HttpMethod.GET, "/api/v1/feeds", "/api/v1/feeds/recommands",
                        "/api/v1/feeds/{id:[0-9]+}", "/api/v1/feeds/{id:[0-9]+}/comments").permitAll()
                // nginx 없이 실행할 때 백엔드가 제공하는 업로드 이미지(IMAGE_STORAGE: 게시글·프로필·배너). nginx와 같이 권한 확인 없이 제공한다.
                .requestMatchers(HttpMethod.GET, "/images/{filename}").permitAll()
                // 통합 검색(개발자·프로젝트 탐색)은 비로그인도 쓴다. 팔로잉 탭의 로그인 확인은 Service에서 한다.
                .requestMatchers(HttpMethod.GET, "/api/v1/search").permitAll()
                // 프리미엄 상품 안내(/subscription)는 비로그인도 볼 수 있다. 결제·구독 관리는 기본 정책(인증 필요)을 따른다.
                .requestMatchers(HttpMethod.GET, "/api/v1/subscription/plans").permitAll()
                // 관리자만 접근 가능
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // 그 외의 모든 요청은 인증된 사용자만 접근 가능
                .anyRequest().authenticated()
            )

            // 커스텀 JWT 필터를 UsernamePasswordAuthenticationFilter 앞에 배치
            .addFilterBefore(
                new JwtAuthenticationFilter(jwtProvider, suspendedMemberStore),
                UsernamePasswordAuthenticationFilter.class
            )

            // 인증·인가 실패를 JSON 으로 응답
            //    - 인증 안 됨(토큰 없음·만료·위조) → 401 (JsonAuthenticationEntryPoint)
            //    - 인증은 됐지만 권한 부족      → 403 (JsonAccessDeniedHandler)
            //    지정하지 않으면 둘 다 403 에 스프링 부트 기본 /error 본문이 내려간다.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(MemberRepository memberRepository) {
        // 로그인 아이디(username)는 이메일이다.
        return email -> {
            Member member = memberRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 사용자입니다: " + email));

            // 카카오로만 가입한 회원은 비밀번호가 없으므로 이메일 로그인을 막는다.
            if (member.getPasswordHash() == null) {
                throw new UsernameNotFoundException("이메일 로그인을 지원하지 않는 계정입니다: " + email);
            }

            MemberRole role = member.getRole() != null ? member.getRole() : MemberRole.USER;

            // 정지 여부는 여기서 표시하지 않는다. 스프링 시큐리티는 잠금을 비밀번호보다 먼저 확인하므로,
            // 여기서 잠그면 비밀번호를 몰라도 정지 계정인지 드러난다. 정지 확인은 AuthService가 비밀번호 확인 뒤에 한다.
            return User.builder()
                    .username(member.getEmail())
                    .password(member.getPasswordHash())
                    .roles(role.name())
                    .build();
        };
    }
}

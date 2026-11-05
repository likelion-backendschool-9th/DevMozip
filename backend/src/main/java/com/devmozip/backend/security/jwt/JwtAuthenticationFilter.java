package com.devmozip.backend.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 정지된 회원의 요청임을 JsonAuthenticationEntryPoint에 알리는 요청 속성 이름 */
    public static final String SUSPENDED_ATTRIBUTE = "devmozip.suspended";

    private final JwtProvider jwtProvider;
    private final SuspendedMemberStore suspendedMemberStore;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, SuspendedMemberStore suspendedMemberStore) {
        this.jwtProvider = jwtProvider;
        this.suspendedMemberStore = suspendedMemberStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 요청 헤더에서 순수 토큰 추출
        String token = resolveToken(request);

        // 토큰이 존재하고 유효한지 검증
        // 액세스 토큰만 인증에 쓴다. 리프레시 토큰이 오면 인증 정보 없이 넘어가 401이 된다.
        if (token != null && jwtProvider.validateAccessToken(token)) {
            // 토큰에서 사용자 아이디와 권한 추출
            String username = jwtProvider.getUsername(token);
            String role = jwtProvider.getRole(token);

            // 정지된 회원이면 인증 정보를 만들지 않는다. (토큰이 아직 만료되지 않았어도 즉시 차단)
            // 보호된 경로라면 JsonAuthenticationEntryPoint가 이 표시를 보고 403으로 응답한다.
            if (suspendedMemberStore.isSuspended(username)) {
                request.setAttribute(SUSPENDED_ATTRIBUTE, true);
                filterChain.doFilter(request, response);
                return;
            }

            // 스프링 시큐리티 권한 객체 생성
            // 토큰에는 "ADMIN"처럼 순수한 역할 이름만 들어 있다.
            // hasRole("ADMIN")은 "ROLE_ADMIN"을 찾으므로 여기서 접두어를 다시 붙인다.
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

            // 스프링 시큐리티 전용 인증 객체(출입증) 생성
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    Collections.singletonList(authority)
            );

            // 보관함(SecurityContext)에 인증 정보 저장
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 다음 필터로 요청 전달
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        // 헤더에 값이 있고 "Bearer "로 시작하는지 확인한다.
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            // "Bearer " 7글자를 제외한 나머지 순수 토큰 문자열만 잘라서 반환한다.
            return bearerToken.substring(7);
        }

        return null;
    }
}

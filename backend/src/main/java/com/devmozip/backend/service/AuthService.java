package com.devmozip.backend.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Collection;
import java.util.List;

import com.devmozip.backend.dto.auth.LoginRequest;
import com.devmozip.backend.dto.auth.LoginResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberRole;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.jwt.InvalidTokenException;
import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;

@Service
public class AuthService {

    private AuthenticationManager authenticationManager;
    private JwtProvider jwtProvider;
    private RefreshTokenStore refreshTokenStore;
    private MemberRepository memberRepository;

    public AuthService(AuthenticationManager authenticationManager,
                       JwtProvider jwtProvider,
                       RefreshTokenStore refreshTokenStore,
                       MemberRepository memberRepository){
        this.authenticationManager = authenticationManager;
        this.jwtProvider = jwtProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public LoginResponse login(LoginRequest loginRequest){
        // 인증과 토큰 저장 사이에 탈퇴·동일 이메일 재가입이 끼어들지 않게 한다.
        memberRepository.findForAccountUpdateByEmail(loginRequest.username())
                .orElseThrow(() -> new BadCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다."));

        UsernamePasswordAuthenticationToken authenticationToken
            = new UsernamePasswordAuthenticationToken(loginRequest.username(), loginRequest.password());

        Authentication authenticate = authenticationManager.authenticate(authenticationToken);
        // -> 권한 정보 Grant..
        // -> 인증 Authentication
        // -> 보안객체 홀더 SercurityContextHolder

        String username = authenticate.getName();

        // 비밀번호가 맞은 뒤에 정지 여부를 확인한다. (비밀번호가 틀리면 위에서 401로 끝나므로 정지 여부가 드러나지 않는다)
        checkNotSuspended(username);
        String role = toRole(authenticate.getAuthorities());

        return issueTokens(username, role);
    }

    /** 카카오 인증처럼 비밀번호 확인 없이 본인임을 확인한 회원에게 토큰을 발급한다. */
    @Transactional
    public LoginResponse issueTokens(Member member){
        Member current = memberRepository.findForAccountUpdateByEmail(member.getEmail())
                .filter(found -> java.util.Objects.equals(found.getId(), member.getId()))
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 사용자입니다."));
        checkNotSuspended(member.getEmail());
        return issueTokens(current.getEmail(), roleOf(current));
    }

    private LoginResponse issueTokens(String username, String role){

        // 1. 액세스 토큰: 짧게(30분). 요청마다 Authorization 헤더에 실린다.
        String accessToken = jwtProvider.createAccessToken(username, role);

        // 2. 리프레시 토큰: 길게(14일). 액세스 토큰이 만료됐을 때 재발급용으로만 쓴다.
        //    서버가 기억해 두어야 로그아웃 시 무효화할 수 있다.
        String refreshToken = jwtProvider.createRefreshToken(username);
        refreshTokenStore.save(username, refreshToken);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .build();
    }

    /** 리프레시 토큰을 받아 새 액세스 토큰을 발급한다. 리프레시 토큰은 그대로 유지한다. */
    @Transactional
    public LoginResponse refresh(String refreshToken){

        // 1. 서명과 만료 확인 (위조됐거나 14일이 지났으면 거절)
        if (refreshToken == null || !jwtProvider.validateRefreshToken(refreshToken)) {
            throw new InvalidTokenException("리프레시 토큰이 유효하지 않습니다.");
        }

        // 2. 서버가 아직 인정하는 토큰인지 확인 (로그아웃했으면 저장소에 없다)
        String username = jwtProvider.getUsername(refreshToken);
        if (!refreshTokenStore.matches(username, refreshToken)) {
            throw new InvalidTokenException("이미 만료 처리된 리프레시 토큰입니다.");
        }
        Member member = memberRepository.findForAccountUpdateByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 사용자입니다."));
        if (!jwtProvider.validateRefreshToken(refreshToken) || !refreshTokenStore.matches(username, refreshToken)) {
            throw new InvalidTokenException("리프레시 토큰이 유효하지 않습니다.");
        }

        // 3. 로그인 이후 정지됐으면 재발급을 거절하고, 저장된 리프레시 토큰도 지워 이후 재시도를 막는다.
        try {
            checkNotSuspended(username);
        } catch (LockedException e) {
            refreshTokenStore.remove(username);
            throw e;
        }

        // 4. 리프레시 토큰에는 역할이 없으므로 회원 정보를 다시 읽어 역할을 알아낸다.
        //    (그 사이 권한이 바뀌었어도 새 액세스 토큰에는 최신 역할이 담긴다)
        //    비밀번호 없는 카카오 회원도 재발급받아야 하므로 이메일 로그인용 UserDetailsService를 거치지 않는다.
        String accessToken = jwtProvider.createAccessToken(username, roleOf(member));

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .build();
    }

    /** 로그아웃: 저장소에서 지워 같은 리프레시 토큰으로는 더 이상 재발급받지 못하게 한다. */
    public void logout(String refreshToken){
        if (refreshToken == null || !jwtProvider.validateRefreshToken(refreshToken)) {
            return; // 이미 쓸 수 없는 토큰이면 할 일이 없다
        }
        // 저장된 토큰이 이 토큰일 때만 지운다. (다른 기기에서 새로 로그인했다면 그 로그인은 유지된다)
        refreshTokenStore.removeMatching(jwtProvider.getUsername(refreshToken), refreshToken);
    }

    /** 정지된 회원이면 LockedException을 던진다. GlobalExceptionHandler가 403으로 응답한다. */
    private void checkNotSuspended(String email){
        boolean suspended = memberRepository.findByEmail(email)
                .map(member -> member.getStatus() == MemberStatus.SUSPENDED)
                .orElse(false);
        if (suspended) {
            throw new LockedException("정지된 계정입니다: " + email);
        }
    }

    /** 회원의 역할 이름. 역할이 비어 있으면 이메일 로그인과 같이 USER로 본다. */
    private String roleOf(Member member){
        return (member.getRole() != null ? member.getRole() : MemberRole.USER).name();
    }

    /**
     * 스프링의 권한 목록을 토큰에 담을 대표 역할 하나로 바꾼다.
     * 토큰은 프론트엔드도 읽는 "공용 데이터"이므로 스프링 내부 규약인 "ROLE_" 접두어는 떼어 낸다.
     * (접두어는 JwtAuthenticationFilter가 토큰을 다시 읽을 때 붙인다)
     * admin 계정은 USER와 ADMIN을 모두 가지므로, 더 높은 권한인 ADMIN을 대표 역할로 담는다.
     */
    private String toRole(Collection<? extends GrantedAuthority> authorities){
        List<String> roles = authorities.stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(authority -> authority.replaceFirst("^ROLE_", ""))
                        .toList();
        return roles.contains("ADMIN") ? "ADMIN" : roles.stream().findFirst().orElse("USER");
    }
}

package com.devmozip.backend.security.jwt;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Member.MemberStatus;
import com.devmozip.backend.repository.MemberRepository;

/**
 * 정지된 회원의 이메일을 기억해 두는 저장소.
 *
 * 액세스 토큰은 요청마다 DB를 확인하지 않으므로(STATELESS), 정지된 회원도 토큰이 만료될 때까지(최대 30분) 쓸 수 있다.
 * JwtAuthenticationFilter가 요청마다 이 목록을 확인해 정지 즉시 모든 API를 막는다. 메모리 조회라 DB 부담이 없다.
 *
 * - 서버 시작 시 DB에서 이미 정지된 회원을 불러온다. (재시작 전 발급된 토큰도 막기 위해)
 * - 관리자 제재 API(회원 정지·해제)를 만들면 그곳에서 suspend() / release()를 호출해야 한다.
 */
@Component
public class SuspendedMemberStore {

    private final Set<String> suspendedEmails = ConcurrentHashMap.newKeySet();
    private final MemberRepository memberRepository;

    public SuspendedMemberStore(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /** 초기 데이터(members_data.sql)가 들어간 뒤에 실행되도록 애플리케이션 준비 완료 시점에 불러온다 */
    @EventListener(ApplicationReadyEvent.class)
    public void loadSuspendedMembers() {
        memberRepository.findAllByStatus(MemberStatus.SUSPENDED).stream()
                .map(Member::getEmail)
                .forEach(suspendedEmails::add);
    }

    public void suspend(String email) {
        suspendedEmails.add(email);
    }

    public void release(String email) {
        suspendedEmails.remove(email);
    }

    public boolean isSuspended(String email) {
        return suspendedEmails.contains(email);
    }
}

package com.devmozip.backend.service;

/**
 * 비밀번호 재설정 메일을 보낸다. 별도 스레드에서 호출하므로 발송 실패를 호출한 쪽에 알리지 않는다.
 *
 * - local 프로필이고 MAIL_HOST가 없으면: ConsolePasswordResetMailSender. 메일 대신 링크를 콘솔에 출력한다.
 * - 그 외: SmtpPasswordResetMailSender.
 */
public interface PasswordResetMailSender {

    /** 지금 발송 요청을 받을 수 있는지 확인한다. false면 재설정 요청에 503으로 응답한다. */
    boolean isAvailable();

    /** 토큰 원문을 담은 재설정 링크를 보낸다. */
    void sendResetLink(String to, String token);

    /** 비밀번호 없는 카카오 회원에게 토큰 없는 안내를 보낸다. */
    void sendKakaoGuide(String to);
}

package com.devmozip.backend.service;

import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.storage.LocalProfileImageStorage;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ProfileImageService {
    private static final Logger log = LoggerFactory.getLogger(ProfileImageService.class);
    private final MemberRepository members;
    private final LocalProfileImageStorage storage;
    private final PlatformTransactionManager transactionManager;

    public enum Kind { PROFILE, BANNER }
    public record ImageResponse(String profileImage, String bannerImage) {}

    @Transactional
    public ImageResponse upload(String authenticatedEmail, Kind kind, MultipartFile file) {
        var member = findOwner(authenticatedEmail);
        String previous = current(member, kind);
        var saved = storage.save(file);
        // DB 실패면 새 파일 정리, DB 성공 이후에만 이전 파일 정리.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) deleteIfUnreferenced(previous);
                else deleteQuietly(saved.filename());
            }
        });
        assign(member, kind, saved.url());
        return response(member);
    }

    @Transactional
    public ImageResponse delete(String authenticatedEmail, Kind kind) {
        var member = findOwner(authenticatedEmail);
        String previous = current(member, kind);
        assign(member, kind, null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) deleteIfUnreferenced(previous);
            }
        });
        return response(member);
    }

    @Transactional(readOnly = true)
    public byte[] readPublicImage(String filename) {
        String url = storage.url(filename);
        if (!members.existsPublicImage(url, Member.MemberStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.");
        }
        return storage.read(filename);
    }

    private Member findOwner(String email) {
        if (email == null || email.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        var member = members.findForImageUpdateByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."));
        if (member.getStatus() != Member.MemberStatus.ACTIVE && member.getStatus() != Member.MemberStatus.WARNED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 계정 상태에서는 이미지를 수정할 수 없습니다.");
        }
        return member;
    }

    private void deleteIfUnreferenced(String previous) {
        String filename = storage.ownedFilename(previous);
        if (filename == null) return; // 외부 URL과 이 저장소 밖의 파일은 삭제하지 않는다.
        try {
            var transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            transaction.setReadOnly(true);
            Boolean referenced = transaction.execute(status -> members.existsByProfileImageOrBannerImage(previous, previous));
            if (Boolean.FALSE.equals(referenced)) storage.delete(filename);
        } catch (RuntimeException | IOException exception) {
            // 이미 확정된 DB 변경을 실패 응답으로 뒤집지 않는다. 파일은 후속 정리 대상으로 남긴다.
            log.warn("사용하지 않는 프로필 이미지 파일 정리에 실패했습니다: {}", filename);
        }
    }

    private void deleteQuietly(String filename) {
        try { storage.delete(filename); }
        catch (IOException exception) { log.warn("실패한 업로드 파일 정리에 실패했습니다: {}", filename); }
    }

    private static String current(Member member, Kind kind) { return kind == Kind.PROFILE ? member.getProfileImage() : member.getBannerImage(); }
    private static void assign(Member member, Kind kind, String url) {
        if (kind == Kind.PROFILE) member.setProfileImage(url); else member.setBannerImage(url);
    }
    private static ImageResponse response(Member member) { return new ImageResponse(member.getProfileImage(), member.getBannerImage()); }
}

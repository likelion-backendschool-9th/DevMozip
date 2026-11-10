package com.devmozip.backend.service.impl;

import com.devmozip.backend.dto.portfolio.PortfolioFileResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.PortfolioFileAccessRepository;
import com.devmozip.backend.service.PortfolioFileService;
import com.devmozip.backend.storage.LocalPortfolioFileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import static com.devmozip.backend.storage.LocalPortfolioFileStorage.failure;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class PortfolioFileServiceImpl implements PortfolioFileService {
    private final MemberRepository members;
    private final LocalPortfolioFileStorage storage;
    private final PortfolioFileAccessRepository access;
    public PortfolioFileResponse upload(String email, MultipartFile file) {
        Member member = viewer(email);
        if (member == null) throw failure(401, "로그인이 필요합니다.");
        return storage.save(member.getId(), file).response();
    }
    public FileContent read(String email, String id) {
        var meta = authorize(email, id);
        return new FileContent(meta, storage.read(meta));
    }
    public FileLocation locate(String email, String id) {
        var meta = authorize(email, id);
        return new FileLocation(meta, storage.storedName(meta));
    }
    private LocalPortfolioFileStorage.Metadata authorize(String email, String id) {
        var meta = storage.metadata(id); Member member = viewer(email);
        if (member == null || member.getId() != meta.ownerId()) {
            try {
                boolean cover = meta.contentType().startsWith("image/") && access.publicReference(meta.ownerId(), meta.response().url(), true);
                boolean premium = member != null && (member.getRole() == Member.MemberRole.PREMIUM || member.getRole() == Member.MemberRole.ADMIN);
                if (!cover && !(premium && (access.publicReference(meta.ownerId(), meta.response().url(), false) || access.submittedReference(meta.ownerId(),member.getId(),meta.response().url())))) throw failure(404, "파일을 찾을 수 없습니다.");
            } catch (DataAccessException e) { throw failure(503, "파일 접근 정보를 확인할 수 없습니다."); }
        }
        return meta;
    }
    private Member viewer(String email) {
        if (email == null || email.isBlank()) return null;
        Member m = members.findByEmail(email).orElseThrow(() -> failure(401, "로그인이 필요합니다."));
        if (m.getStatus() != Member.MemberStatus.ACTIVE && m.getStatus() != Member.MemberStatus.WARNED) throw failure(403, "이 계정으로 파일을 사용할 수 없습니다.");
        return m;
    }
}

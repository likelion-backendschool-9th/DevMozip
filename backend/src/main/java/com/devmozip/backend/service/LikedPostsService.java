package com.devmozip.backend.service;

import com.devmozip.backend.dto.profile.LikedPostResponse;
import com.devmozip.backend.dto.profile.LikedPostsPageResponse;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.LikedPostsQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class LikedPostsService {
    private final MemberRepository members;
    private final LikedPostsQueryRepository query;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public LikedPostsPageResponse findOwn(String email, int page, int size) {
        if (email == null || email.isBlank()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        if (page < 0 || page > 1000000 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지와 조회 개수를 확인해 주세요.");
        var member = members.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."));
        if (member.getStatus() != Member.MemberStatus.ACTIVE && member.getStatus() != Member.MemberStatus.WARNED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이 계정으로 목록을 조회할 수 없습니다.");
        }
        try {
            var result = query.findByUserId(member.getId(), page, size);
            return new LikedPostsPageResponse(result.rows().stream().map(this::response).toList(),page,size,result.total(),
                    result.total() / size + (result.total() % size == 0 ? 0 : 1));
        } catch (DataAccessException exception) {
            // SNS 테이블 미준비·DB 오류를 빈 목록으로 위장하지 않고 내부 SQL도 전달하지 않는다.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "좋아요한 글 목록을 조회할 수 없습니다.");
        }
    }
    private LikedPostResponse response(LikedPostsQueryRepository.Row row) {
        var original = row.original() == null ? null : new LikedPostResponse.OriginalPost(row.original().id(),row.original().contentPreview(),author(row.original().author()));
        return new LikedPostResponse(row.id(),row.type(),row.category(),row.contentPreview(),author(row.author()),atSeoul(row.createdAt()),atSeoul(row.likedAt()),original);
    }
    private LikedPostResponse.Author author(LikedPostsQueryRepository.Author row) {
        return new LikedPostResponse.Author(row.id(),row.nickname(),row.tag(),row.profileImage());
    }
    // ERD DATETIME은 한국 시각의 timestamp without time zone으로 가정한다.
    private OffsetDateTime atSeoul(LocalDateTime value) { return value == null ? null : value.atOffset(ZoneOffset.ofHours(9)); }
}

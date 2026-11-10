package com.devmozip.backend.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** SNS 소유 테이블을 조회만 한다. Entity·DDL·좋아요 추가/취소 기능은 생성하지 않는다. */
@Repository
public class ProfilePostsQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ProfilePostsQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Author(long id, String nickname, String tag, String profileImage) {}
    public record Original(long id, String contentPreview, Author author) {}
    public record Row(long id, String type, String category, String title, String contentPreview, Author author,
            LocalDateTime createdAt, Original original) {}
    public record Result(List<Row> rows, long total) {}

    // 피드 설계 기준 값: VISIBLE, POST, REPOST. 다른 값·공개 여부 불명인 원본은 노출하지 않는다.
    private static final String SOURCE = """
            FROM posts p
            JOIN users a ON a.id=p.author_id
            LEFT JOIN posts o ON o.id=p.original_post_id
            LEFT JOIN users oa ON oa.id=o.author_id
            WHERE p.author_id=:userId AND p.deleted_at IS NULL AND p.visibility='VISIBLE'
              AND a.status IN ('ACTIVE','WARNED')
              AND ((p.type='POST' AND p.original_post_id IS NULL)
                OR (p.type='REPOST' AND o.id IS NOT NULL AND o.type='POST'
                    AND o.original_post_id IS NULL AND o.visibility='VISIBLE'
                    AND o.deleted_at IS NULL AND oa.status='ACTIVE'))
            """;
    public Result findByUserId(long userId, int page, int size) {
        var params = new MapSqlParameterSource("userId", userId);
        Long total = jdbc.queryForObject("SELECT count(*) " + SOURCE, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        String sql = """
                SELECT p.id,p.type,p.category,p.title,left(p.content,300) AS content_preview,p.created_at,
                  a.id AS author_id,a.nickname AS author_nickname,a.tag AS author_tag,a.profile_image AS author_image,
                  o.id AS original_id,left(o.content,300) AS original_preview,
                  oa.id AS original_author_id,oa.nickname AS original_author_nickname,
                  oa.tag AS original_author_tag,oa.profile_image AS original_author_image
                """ + SOURCE + " ORDER BY p.created_at DESC NULLS LAST,p.id DESC LIMIT :limit OFFSET :offset";
        return new Result(jdbc.query(sql, params, (rs, index) -> row(rs)), total == null ? 0 : total);
    }
    private Row row(ResultSet rs) throws SQLException {
        Long originalId = rs.getObject("original_id", Long.class);
        var original = originalId == null ? null : new Original(originalId, rs.getString("original_preview"),
                new Author(rs.getLong("original_author_id"),rs.getString("original_author_nickname"),rs.getString("original_author_tag"),rs.getString("original_author_image")));
        return new Row(rs.getLong("id"),rs.getString("type"),rs.getString("category"),rs.getString("title"),rs.getString("content_preview"),
                new Author(rs.getLong("author_id"),rs.getString("author_nickname"),rs.getString("author_tag"),rs.getString("author_image")),
                rs.getObject("created_at",LocalDateTime.class),original);
    }
}

package com.devmozip.backend.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 제공된 ERD의 portfolios를 조회만 한다. 공유 DB DDL이나 작성용 Entity는 만들지 않는다. */
@Repository
public class PortfolioQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public PortfolioQueryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Summary(long id, long userId, String title, String thumbnailUrl, String topic,
            String summary, String role, String techStack, String visibility, LocalDateTime createdAt,
            LocalDateTime updatedAt) {}
    public record Page(List<Summary> rows, long total) {}
    public record Access(long id, long userId, String visibility, String ownerStatus) {}
    public record Detail(Summary summary, String description, String attachments,
            String nickname, String tag, String profileImage) {}
    private static final String COLUMNS = """
            p.id,p.user_id,p.title,p.thumbnail_url,p.topic,p.summary,p.role,p.tech_stack,
            p.visibility,p.created_at,p.updated_at
            """;
    public Page list(long userId, boolean own, int page, int size) {
        var params = new MapSqlParameterSource("userId",userId).addValue("size",size).addValue("offset",(long)page*size);
        String source = " FROM portfolios p WHERE p.user_id=:userId AND "
                + (own ? "p.visibility IN ('PUBLIC','PRIVATE')" : "p.visibility='PUBLIC'");
        Long total = jdbc.queryForObject("SELECT count(*)"+source,params,Long.class);
        List<Summary> rows = jdbc.query("SELECT "+COLUMNS+source+
                " ORDER BY p.created_at DESC NULLS LAST,p.id DESC LIMIT :size OFFSET :offset",params,(rs,n)->summary(rs));
        return new Page(rows,total==null?0:total);
    }
    public Optional<Access> access(long id) {
        return jdbc.query("""
                SELECT p.id,p.user_id,p.visibility,u.status FROM portfolios p
                JOIN users u ON u.id=p.user_id WHERE p.id=:id
                """,new MapSqlParameterSource("id",id),(rs,n)->new Access(rs.getLong("id"),rs.getLong("user_id"),
                        rs.getString("visibility"),rs.getString("status"))).stream().findFirst();
    }
    public Optional<Detail> detail(long id) {
        return jdbc.query("SELECT "+COLUMNS+"""
                ,p.description,p.attachments,u.nickname,u.tag,u.profile_image
                FROM portfolios p JOIN users u ON u.id=p.user_id WHERE p.id=:id
                """,new MapSqlParameterSource("id",id),(rs,n)->new Detail(summary(rs),rs.getString("description"),
                        rs.getString("attachments"),rs.getString("nickname"),rs.getString("tag"),rs.getString("profile_image")))
                .stream().findFirst();
    }
    private Summary summary(ResultSet rs) throws SQLException {
        return new Summary(rs.getLong("id"),rs.getLong("user_id"),rs.getString("title"),rs.getString("thumbnail_url"),
                rs.getString("topic"),rs.getString("summary"),rs.getString("role"),rs.getString("tech_stack"),
                rs.getString("visibility"),rs.getObject("created_at",LocalDateTime.class),rs.getObject("updated_at",LocalDateTime.class));
    }
}

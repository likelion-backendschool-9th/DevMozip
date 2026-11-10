package com.devmozip.backend.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** ERD의 portfolios에만 쓰며 테이블·컬럼·제약을 자동 생성하지 않는다. */
@Repository
public class PortfolioMutationRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public PortfolioMutationRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Stored(String title, String thumbnailUrl, String topic, String summary, String description,
            String role, String techStack, String attachments, String visibility) {}
    public record Locked(long userId, String visibility, LocalDateTime updatedAt) {}
    public Optional<Locked> lock(long id) {
        return jdbc.query("SELECT user_id,visibility,updated_at FROM portfolios WHERE id=:id FOR UPDATE",
                new MapSqlParameterSource("id",id),(rs,n)->new Locked(rs.getLong("user_id"),rs.getString("visibility"),
                        rs.getObject("updated_at",LocalDateTime.class))).stream().findFirst();
    }
    public long create(long userId,Stored value,LocalDateTime now) {
        var params=params(value,now).addValue("userId",userId);
        Long id=jdbc.queryForObject("""
                INSERT INTO portfolios(user_id,title,thumbnail_url,topic,summary,description,role,tech_stack,attachments,visibility,created_at,updated_at)
                VALUES(:userId,:title,:thumbnail,:topic,:summary,:description,:role,CAST(:stack AS jsonb),CAST(:attachments AS jsonb),:visibility,:now,:now)
                RETURNING id
                """,params,Long.class);
        if(id==null)throw new IllegalStateException("포트폴리오 ID를 확인할 수 없습니다.");
        return id;
    }
    public void update(long id,long userId,Stored value,LocalDateTime now) {
        jdbc.update("""
                UPDATE portfolios SET title=:title,thumbnail_url=:thumbnail,topic=:topic,summary=:summary,description=:description,
                  role=:role,tech_stack=CAST(:stack AS jsonb),attachments=CAST(:attachments AS jsonb),updated_at=:now
                WHERE id=:id AND user_id=:userId
                """,params(value,now).addValue("id",id).addValue("userId",userId));
    }
    public boolean referenceConstraintReady() {
        // FK가 없으면 삭제와 지원서 추가의 경쟁에서 고아 참조를 막을 수 없어 삭제를 허용하지 않는다.
        Boolean ready=jdbc.queryForObject("""
                SELECT EXISTS(
                  SELECT 1 FROM pg_constraint c
                  JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1]
                  JOIN pg_attribute r ON r.attrelid=c.confrelid AND r.attnum=c.confkey[1]
                  WHERE c.conrelid=to_regclass('public.project_application_portfolios')
                    AND c.confrelid=to_regclass('public.portfolios') AND c.contype='f'
                    AND array_length(c.conkey,1)=1 AND a.attname='portfolio_id' AND r.attname='id'
                    AND c.confdeltype IN ('a','r','n') AND c.convalidated AND NOT c.condeferrable)
                """,new MapSqlParameterSource(),Boolean.class);
        return Boolean.TRUE.equals(ready);
    }
    public boolean referenced(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM project_application_portfolios WHERE portfolio_id=:id)",
                new MapSqlParameterSource("id",id),Boolean.class));
    }
    public void changeVisibility(long id,long userId,String visibility,LocalDateTime now) {
        jdbc.update("UPDATE portfolios SET visibility=:visibility,updated_at=:now WHERE id=:id AND user_id=:userId",
                new MapSqlParameterSource("id",id).addValue("userId",userId).addValue("visibility",visibility).addValue("now",now));
    }
    public boolean submittedCopiesReady(long id) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.project_application_portfolio_snapshots') IS NOT NULL",new MapSqlParameterSource(),Boolean.class)))return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT NOT EXISTS(SELECT 1 FROM project_application_portfolios l
                LEFT JOIN project_application_portfolio_snapshots s ON s.application_portfolio_id=l.id
                WHERE l.portfolio_id=:id AND (s.id IS NULL OR s.source_portfolio_id<>:id OR s.content IS NULL))
                """,new MapSqlParameterSource("id",id),Boolean.class));
    }
    public void delete(long id,long userId) {
        jdbc.update("DELETE FROM portfolios WHERE id=:id AND user_id=:userId",new MapSqlParameterSource("id",id).addValue("userId",userId));
    }
    private MapSqlParameterSource params(Stored p,LocalDateTime now) {
        return new MapSqlParameterSource("title",p.title()).addValue("thumbnail",p.thumbnailUrl()).addValue("topic",p.topic())
                .addValue("summary",p.summary()).addValue("description",p.description()).addValue("role",p.role())
                .addValue("stack",p.techStack()).addValue("attachments",p.attachments()).addValue("visibility",p.visibility()).addValue("now",now);
    }
}

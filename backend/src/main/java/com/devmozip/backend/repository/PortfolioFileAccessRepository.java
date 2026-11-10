package com.devmozip.backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PortfolioFileAccessRepository {
    private final JdbcTemplate jdbc;
    public PortfolioFileAccessRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public boolean publicReference(long ownerId, String url, boolean coverOnly) {
        // 반드시 원래 업로더의 포트폴리오에 연결된 파일만 공개한다. 설명에 UUID가 등장해도 타인 파일은 공개되지 않는다.
        String reference = coverOnly ? "p.thumbnail_url=?" : "(p.thumbnail_url=? OR position(? in coalesce(p.description,''))>0 OR EXISTS(SELECT 1 FROM jsonb_array_elements(CASE WHEN jsonb_typeof(p.attachments)='array' THEN p.attachments ELSE '[]'::jsonb END) a WHERE a->>'url'=?))";
        String sql = "SELECT EXISTS(SELECT 1 FROM portfolios p JOIN users u ON u.id=p.user_id WHERE p.user_id=? AND p.visibility='PUBLIC' AND u.status='ACTIVE' AND " + reference + ")";
        return Boolean.TRUE.equals(coverOnly ? jdbc.queryForObject(sql, Boolean.class, ownerId, url)
                : jdbc.queryForObject(sql, Boolean.class, ownerId, url, url, url));
    }
    public boolean submittedReference(long ownerId,long viewerId,String url) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('public.project_application_portfolio_snapshots') IS NOT NULL",Boolean.class)))return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM project_application_portfolio_snapshots s
                JOIN project_application_portfolios l ON l.id=s.application_portfolio_id
                JOIN project_applications a ON a.id=l.application_id JOIN projects p ON p.id=a.project_id
                WHERE a.applicant_member_id=? AND p.host_member_id=? AND position(? in s.content::text)>0)
                """,Boolean.class,ownerId,viewerId,url));
    }
}

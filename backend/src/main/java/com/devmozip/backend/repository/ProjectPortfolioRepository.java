package com.devmozip.backend.repository;

import java.util.List;
import com.devmozip.backend.service.ProjectPortfolioAccess.Portfolio;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectPortfolioRepository {
	private final NamedParameterJdbcTemplate jdbc;
	public ProjectPortfolioRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
	private static final String OWN_PUBLIC=" FROM portfolios p JOIN users u ON u.id=p.user_id WHERE p.user_id=:user AND p.visibility='PUBLIC' AND u.status='ACTIVE'";
	public List<Portfolio> ownedPublic(long user){return jdbc.query("SELECT p.id,p.title"+OWN_PUBLIC+" ORDER BY p.created_at DESC,p.id DESC",new MapSqlParameterSource("user",user),(rs,n)->new Portfolio(rs.getLong("id"),rs.getString("title")));}
	public List<Long> lockOwnedPublic(long user,List<Long> ids){return jdbc.query("SELECT p.id"+OWN_PUBLIC+" AND p.id IN (:ids) ORDER BY p.id FOR UPDATE OF p",new MapSqlParameterSource("user",user).addValue("ids",ids),(rs,n)->rs.getLong("id"));}
	public boolean ready(){return Boolean.TRUE.equals(jdbc.queryForObject("""
		SELECT EXISTS(SELECT 1 FROM pg_constraint c
		 JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1]
		 JOIN pg_attribute b ON b.attrelid=c.confrelid AND b.attnum=c.confkey[1]
		 WHERE c.conrelid=to_regclass('public.project_application_portfolios') AND c.confrelid=to_regclass('public.portfolios')
		 AND c.contype='f' AND array_length(c.conkey,1)=1 AND a.attname='portfolio_id' AND b.attname='id'
		 AND c.confdeltype='n' AND c.convalidated AND NOT c.condeferrable)
		 AND EXISTS(SELECT 1 FROM pg_constraint c
		 JOIN pg_attribute a ON a.attrelid=c.conrelid AND a.attnum=c.conkey[1]
		 WHERE c.conrelid=to_regclass('public.project_application_portfolio_snapshots')
		 AND c.confrelid=to_regclass('public.project_application_portfolios') AND c.contype='f'
		 AND array_length(c.conkey,1)=1 AND a.attname='application_portfolio_id' AND c.convalidated AND NOT c.condeferrable)
		""",new MapSqlParameterSource(),Boolean.class));}
}

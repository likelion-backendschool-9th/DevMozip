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
	public List<Portfolio> ownedPublic(long user){
		return jdbc.query("SELECT p.id,p.title"+OWN_PUBLIC+" ORDER BY p.created_at DESC,p.id DESC",new MapSqlParameterSource("user",user),(rs,n)->new Portfolio(rs.getLong("id"),rs.getString("title")));
	}
	public List<Long> lockOwnedPublic(long user,List<Long> ids){
		return jdbc.query("SELECT p.id"+OWN_PUBLIC+" AND p.id IN (:ids) ORDER BY p.id FOR UPDATE OF p",new MapSqlParameterSource("user",user).addValue("ids",ids),(rs,n)->rs.getLong("id"));
	}
}

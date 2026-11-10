package com.devmozip.backend.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import com.devmozip.backend.dto.portfolio.PortfolioDetailResponse;
import com.devmozip.backend.entity.*;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.service.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

public class DatabaseProjectPortfolioAccess implements ProjectPortfolioAccess {
	private final ProjectPortfolioRepository portfolios;
	private final ProjectApplicationPortfolioSnapshotRepository snapshots;
	private final PortfolioQueryService queries;
	private final MemberRepository users;
	private final ObjectMapper mapper;
	public DatabaseProjectPortfolioAccess(ProjectPortfolioRepository portfolios,ProjectApplicationPortfolioSnapshotRepository snapshots,PortfolioQueryService queries,MemberRepository users,ObjectMapper mapper){this.portfolios=portfolios;this.snapshots=snapshots;this.queries=queries;this.users=users;this.mapper=mapper;}
	@Override public boolean available(){try{return portfolios.ready();}catch(DataAccessException e){return false;}}
	@Override public List<Portfolio> ownedBy(long user){try{return portfolios.ownedPublic(user);}catch(DataAccessException e){throw unavailable();}}
	@Override @Transactional(propagation=Propagation.MANDATORY)
	public boolean ownsAll(long user,List<Long> ids){
		if(ids==null||ids.isEmpty()||ids.size()>3||ids.stream().anyMatch(id->id==null||id<=0)||ids.stream().distinct().count()!=ids.size())return false;
		try{return portfolios.lockOwnedPublic(user,ids).size()==ids.size();}catch(DataAccessException e){throw unavailable();}
	}
	@Override @Transactional(propagation=Propagation.MANDATORY)
	public void capture(ProjectApplicationPortfolio submission,LocalDateTime now){
		var user=users.findById(submission.getApplication().getApplicantMemberId()).orElseThrow(()->unavailable());
		var original=queries.detail(user.getEmail(),submission.getPortfolioId());
		var copy=new PortfolioDetailResponse(original.portfolio(),original.author(),original.description(),original.attachments(),original.document(),false);
		snapshots.saveAndFlush(new ProjectApplicationPortfolioSnapshot(submission,submission.getPortfolioId(),original.portfolio().title(),mapper.writeValueAsString(copy),now));
	}
	private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"포트폴리오 제출 저장소가 준비되지 않았습니다.");}
}

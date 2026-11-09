package com.devmozip.backend.service.impl;

import java.util.List;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.service.ProjectPortfolioAccess;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

public class DatabaseProjectPortfolioAccess implements ProjectPortfolioAccess {
	private final ProjectPortfolioRepository portfolios;
	private final PortfolioMutationRepository mutations;
	public DatabaseProjectPortfolioAccess(ProjectPortfolioRepository portfolios,PortfolioMutationRepository mutations){this.portfolios=portfolios;this.mutations=mutations;}
	@Override public boolean available(){try{return mutations.referenceConstraintReady();}catch(DataAccessException e){return false;}}
	@Override public List<Portfolio> ownedBy(long user){try{return portfolios.ownedPublic(user);}catch(DataAccessException e){throw unavailable();}}
	@Override @Transactional(propagation=Propagation.MANDATORY)
	public boolean ownsAll(long user,List<Long> ids){
		if(ids==null||ids.isEmpty()||ids.size()>3||ids.stream().anyMatch(id->id==null||id<=0)||ids.stream().distinct().count()!=ids.size())return false;
		try{return portfolios.lockOwnedPublic(user,ids).size()==ids.size();}catch(DataAccessException e){throw unavailable();}
	}
	private ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"포트폴리오 저장소에 연결할 수 없습니다.");}
}

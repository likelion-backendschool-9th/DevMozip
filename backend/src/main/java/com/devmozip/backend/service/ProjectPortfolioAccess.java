package com.devmozip.backend.service;
import java.util.List;
import java.time.LocalDateTime;
import com.devmozip.backend.entity.ProjectApplicationPortfolio;

/** 포트폴리오 담당자의 실제 존재·소유·삭제 정책을 연결할 경계. Mock ID를 실회원 ID로 간주하지 않는다. */
public interface ProjectPortfolioAccess {
	record Portfolio(long id,String title) {}
	boolean available();
	List<Portfolio> ownedBy(long userId);
	boolean ownsAll(long userId,List<Long> portfolioIds);
	void capture(ProjectApplicationPortfolio submission,LocalDateTime now);
}

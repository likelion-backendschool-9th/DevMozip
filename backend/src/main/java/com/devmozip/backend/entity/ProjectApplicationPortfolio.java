package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="project_application_portfolios",uniqueConstraints=@UniqueConstraint(columnNames={"application_id","portfolio_id"}))
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ProjectApplicationPortfolio extends BaseEntity {
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="application_id",nullable=false) private ProjectApplication application;
	// 실제 ERD/DB의 NULL 허용을 유지한다. 신규 제출은 DTO/Service에서 양수 ID를 강제한다.
	@Column(name="portfolio_id") private Long portfolioId;
	public ProjectApplicationPortfolio(ProjectApplication application,long portfolioId,LocalDateTime now){super(now);this.application=application;this.portfolioId=portfolioId;}
}

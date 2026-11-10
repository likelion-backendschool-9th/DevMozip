package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name="project_application_portfolio_snapshots")
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ProjectApplicationPortfolioSnapshot extends BaseEntity {
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
	@OneToOne(fetch=FetchType.LAZY,optional=false)
	@JoinColumn(name="application_portfolio_id",nullable=false,unique=true,updatable=false)
	private ProjectApplicationPortfolio submission;
	// 원본 삭제 이후에도 유지할 식별자. portfolios FK를 만들지 않는다.
	@Column(nullable=false,updatable=false) private long sourcePortfolioId;
	@Column(nullable=false,length=255,updatable=false) private String title;
	@JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb",updatable=false) private String content;
	public ProjectApplicationPortfolioSnapshot(ProjectApplicationPortfolio submission,long sourcePortfolioId,String title,String content,LocalDateTime now){
		super(now);this.submission=submission;this.sourcePortfolioId=sourcePortfolioId;this.title=title;this.content=content;
	}
}

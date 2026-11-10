package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="review_keyword_selections",uniqueConstraints=@UniqueConstraint(columnNames={"review_id","keyword_id"}))
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ReviewKeywordSelection extends BaseEntity {
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
	@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="review_id",nullable=false) private ProjectReview review;
	@ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="keyword_id",nullable=false) private ReviewKeyword keyword;
	public ReviewKeywordSelection(ProjectReview review,ReviewKeyword keyword,LocalDateTime now){super(now);this.review=review;this.keyword=keyword;}
}

package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="review_keywords",uniqueConstraints=@UniqueConstraint(columnNames="name"))
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class ReviewKeyword extends BaseEntity {
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY)
	private Long id;
	@Column(nullable=false,length=100)
	private String name;
	@Column(name="is_active",nullable=false)
	private boolean active;

	public ReviewKeyword(String name,LocalDateTime now){super(now);this.name=name;this.active=true;}
	// 관리자 키워드 관리(WBS 14.9). 행을 지우지 않고 새 리뷰의 선택지에서만 빼거나 다시 넣는다. 기존 리뷰 연결은 그대로다.
	public void changeActive(boolean active,LocalDateTime now){if(this.active!=active){this.active=active;touch(now);}}
}

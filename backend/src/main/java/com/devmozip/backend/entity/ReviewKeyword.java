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
}

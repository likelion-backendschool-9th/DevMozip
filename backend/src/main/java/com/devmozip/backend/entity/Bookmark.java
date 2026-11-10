package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name="bookmarks",uniqueConstraints={@UniqueConstraint(columnNames={"user_id","project_id"}),@UniqueConstraint(columnNames={"user_id","post_id"})})
@Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Bookmark extends BaseEntity {
	@Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
	@Column(name="user_id",nullable=false) private long userId;
	@ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="project_id") private Project project;
	@Column(name="post_id") private Long postId;
	public Bookmark(long userId,Project project,LocalDateTime now){super(now);this.userId=userId;this.project=project;}
}

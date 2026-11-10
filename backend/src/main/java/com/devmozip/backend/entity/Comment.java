package com.devmozip.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 게시글 댓글 (ERD comments). 삭제는 deleted_at으로 표시한다. */
@Entity
@Table(name = "comments", indexes = @Index(name = "comments_post_created_idx", columnList = "post_id, created_at, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseEntity {
	public static final int MAX_CONTENT_LENGTH = 1000;

	// 댓글 ID
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 게시글
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "post_id", nullable = false)
	private Post post;

	// 작성자
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	private Member author;

	// 댓글 내용
	@Column(name = "content", nullable = false, columnDefinition = "text")
	private String content;

	// 삭제 시각 (논리 삭제)
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	public Comment(Post post, Member author, String content, LocalDateTime now) {
		super(now);
		this.post = post;
		this.author = author;
		this.content = content;
	}

	public void edit(String content, LocalDateTime now) {
		this.content = content;
		touch(now);
	}

	public void softDelete(LocalDateTime now) {
		this.deletedAt = now;
		touch(now);
	}

	public boolean isWrittenBy(long memberId) {
		return author.getId() != null && author.getId() == memberId;
	}

	public boolean isEdited() {
		return getUpdatedAt() != null && getCreatedAt() != null && getUpdatedAt().isAfter(getCreatedAt());
	}
}

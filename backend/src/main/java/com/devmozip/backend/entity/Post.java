package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.devmozip.backend.entity.enums.PostCategory;
import com.devmozip.backend.entity.enums.PostType;
import com.devmozip.backend.entity.enums.PostVisibility;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * 게시글·퍼간 글 (ERD posts, docs/feed-plan.md 2절).
 * JSON 컬럼은 columnDefinition 없이 두어 PostgreSQL은 jsonb, local(H2)은 json으로 만든다. 기본값 '[]'은 엔티티가 항상 채운다.
 */
@Entity
@Table(name = "posts", indexes = {
	@Index(name = "posts_created_id_idx", columnList = "created_at DESC, id DESC"),
	@Index(name = "posts_author_created_idx", columnList = "author_id, created_at DESC, id DESC"),
	@Index(name = "posts_original_idx", columnList = "original_post_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {
	public static final int MAX_TITLE_LENGTH = 150;

	// 게시글 ID
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 작성자
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false)
	private Member author;

	// 원본 게시글. 퍼간 글만 채운다
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "original_post_id")
	private Post originalPost;

	// 게시글 유형
	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 20)
	private PostType type;

	// 카테고리. 퍼간 글은 원본의 카테고리를 따른다
	@Enumerated(EnumType.STRING)
	@Column(name = "category", nullable = false, length = 30)
	private PostCategory category;

	// 제목. 원본 글은 필수, 퍼간 글은 비운다
	@Column(name = "title", length = MAX_TITLE_LENGTH)
	private String title;

	// 내용. 퍼간 글이면 의견이며 비어 있을 수 있다
	@Column(name = "content", columnDefinition = "text")
	private String content;

	// 기술 스택
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "tech_stacks", nullable = false)
	private List<String> techStacks;

	// 관심 분야
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "interests", nullable = false)
	private List<String> interests;

	// 이미지 경로(/images/...) 배열. 표시 순서대로 둔다
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "image_urls", nullable = false)
	private List<String> imageUrls;

	// 조회수. 증가는 PostRepository.increaseViewCount로 한다
	@Column(name = "view_count", nullable = false)
	private long viewCount;

	// 공개 범위
	@Enumerated(EnumType.STRING)
	@Column(name = "visibility", nullable = false, length = 20)
	private PostVisibility visibility;

	// 삭제 시각 (논리 삭제)
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	/** 원본 글을 만든다. 이미지는 게시글 id가 생긴 뒤 replaceImageUrls로 채운다. */
	public static Post write(Member author, PostCategory category, String title, String content,
		List<String> techStacks, List<String> interests, LocalDateTime now) {
		Post post = new Post(author, PostType.POST, category, now);
		post.title = title;
		post.content = content;
		post.techStacks = List.copyOf(techStacks);
		post.interests = List.copyOf(interests);
		return post;
	}

	/** 원본을 퍼간 글을 만든다. 제목·태그·이미지는 비우고 의견만 둔다. */
	public static Post repost(Member author, Post original, String opinion, LocalDateTime now) {
		Post post = new Post(author, PostType.REPOST, original.getCategory(), now);
		post.originalPost = original;
		post.content = opinion;
		return post;
	}

	private Post(Member author, PostType type, PostCategory category, LocalDateTime now) {
		super(now);
		this.author = author;
		this.type = type;
		this.category = category;
		this.techStacks = List.of();
		this.interests = List.of();
		this.imageUrls = List.of();
		this.viewCount = 0;
		this.visibility = PostVisibility.VISIBLE;
	}

	/** 원본 글 수정 (8.6). 공개 범위는 바꾸지 않는다(작성자 숨김은 WBS 3.8). */
	public void edit(PostCategory category, String title, String content, List<String> techStacks,
		List<String> interests, List<String> imageUrls, LocalDateTime now) {
		this.category = category;
		this.title = title;
		this.content = content;
		this.techStacks = List.copyOf(techStacks);
		this.interests = List.copyOf(interests);
		this.imageUrls = List.copyOf(imageUrls);
		touch(now);
	}

	/** 퍼간 글의 의견 수정 (8.6) */
	public void editOpinion(String opinion, LocalDateTime now) {
		this.content = opinion;
		touch(now);
	}

	/** 작성 직후 저장한 이미지 경로를 채운다. 작성 시각과 수정 시각을 같게 두려고 수정 시각은 바꾸지 않는다. */
	public void replaceImageUrls(List<String> imageUrls) {
		this.imageUrls = List.copyOf(imageUrls);
	}

	public void softDelete(LocalDateTime now) {
		this.deletedAt = now;
		touch(now);
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public boolean isRepost() {
		return type == PostType.REPOST;
	}

	public boolean isWrittenBy(long memberId) {
		return author.getId() != null && author.getId() == memberId;
	}

	/** 수정한 적이 있는지. 좋아요·조회수는 이 행의 수정 시각을 바꾸지 않는다. */
	public boolean isEdited() {
		return getUpdatedAt() != null && getCreatedAt() != null && getUpdatedAt().isAfter(getCreatedAt());
	}
}

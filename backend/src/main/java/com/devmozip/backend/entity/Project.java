package com.devmozip.backend.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import com.devmozip.backend.entity.enums.ProjectStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseEntity {
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "host_member_id", nullable = false)
	private long hostMemberId;
	@Column(nullable = false, length = 255)
	private String title;
	@Column(length = 255)
	private String summary;
	@Column(nullable = false, columnDefinition = "text")
	private String description;
	@Column(columnDefinition = "text")
	private String goal;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "recruitment_slots", nullable = false, columnDefinition = "jsonb")
	private List<ProjectRecruitmentSlot> recruitmentSlots;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "tech_stacks", nullable = false, columnDefinition = "jsonb")
	private List<String> techStacks;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<String> interests;
	@Column(name = "image_url", nullable = false, length = 2048)
	private String imageUrl;
	@Column(name = "is_public", nullable = false)
	private boolean isPublic;
	// 관리자 숨김 (WBS 14.5, docs/admin-plan.md D2). 주최자의 공개 여부(is_public)와 따로 두어 주최자가 풀 수 없다.
	// 이미 있는 공고 행이 숨기지 않은 상태로 채워지도록 DB 기본값을 함께 둔다.
	@ColumnDefault("false")
	@Column(name = "is_restricted", nullable = false)
	private boolean isRestricted;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
	private ProjectStatus status;
	@Column(name = "view_count", nullable = false, columnDefinition = "bigint default 0")
	private long viewCount;
	@Column(name = "recruitment_start", nullable = false)
	private LocalDate recruitmentStart;
	@Column(name = "recruitment_end", nullable = false)
	private LocalDate recruitmentEnd;
	@Column(name = "development_start", nullable = false)
	private LocalDate developmentStart;
	@Column(name = "development_end", nullable = false)
	private LocalDate developmentEnd;
	@Column(name = "completed_at")
	private LocalDateTime completedAt;
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	public Project(long hostMemberId, String title, String summary, String description, String goal,
		String imageUrl, boolean isPublic, List<ProjectRecruitmentSlot> recruitmentSlots,
		List<String> techStacks, List<String> interests, LocalDate recruitmentStart,
		LocalDate recruitmentEnd, LocalDate developmentStart, LocalDate developmentEnd, LocalDateTime now) {
		super(now);
		this.hostMemberId = hostMemberId;
		this.title = title;
		this.summary = summary;
		this.description = description;
		this.goal = goal;
		this.imageUrl = imageUrl;
		this.isPublic = isPublic;
		this.recruitmentSlots = List.copyOf(recruitmentSlots);
		this.techStacks = List.copyOf(techStacks);
		this.interests = List.copyOf(interests);
		this.recruitmentStart = recruitmentStart;
		this.recruitmentEnd = recruitmentEnd;
		this.developmentStart = developmentStart;
		this.developmentEnd = developmentEnd;
		this.status = ProjectStatus.RECRUITING;
	}

	public void update(String title, String summary, String description, String goal, String imageUrl,
		boolean isPublic, List<ProjectRecruitmentSlot> slots, List<String> techStacks, List<String> interests,
		LocalDate recruitmentStart, LocalDate recruitmentEnd, LocalDate developmentStart, LocalDate developmentEnd,
		LocalDateTime now) {
		this.title = title;
		this.summary = summary;
		this.description = description;
		this.goal = goal;
		this.imageUrl = imageUrl;
		this.isPublic = isPublic;
		this.recruitmentSlots = List.copyOf(slots);
		this.techStacks = List.copyOf(techStacks);
		this.interests = List.copyOf(interests);
		this.recruitmentStart = recruitmentStart;
		this.recruitmentEnd = recruitmentEnd;
		this.developmentStart = developmentStart;
		this.developmentEnd = developmentEnd;
		touch(now);
	}

	public void changeStatus(ProjectStatus status, LocalDateTime now) {
		this.status = status;
		if (status == ProjectStatus.COMPLETED) completedAt = now;
		touch(now);
	}

	public void extendPeriods(LocalDate recruitmentEnd, LocalDate developmentEnd, LocalDateTime now) {
		this.recruitmentEnd = recruitmentEnd;
		this.developmentEnd = developmentEnd;
		touch(now);
	}

	public void softDelete(LocalDateTime now) {
		deletedAt = now;
		touch(now);
	}

	/** 관리자 숨김·복원 (WBS 14.5). 원하는 최종 값을 받는다. 공고 내용 수정이 아니므로 수정 시각은 바꾸지 않는다. */
	public void restrictByAdmin(boolean restricted) { this.isRestricted = restricted; }

	/**
	 * 팀원이 아닌 사람에게 보이는 공고인지. 주최자가 공개했고 관리자가 숨기지 않았어야 한다.
	 * 비공개 공고와 같이 관리자가 숨긴 공고도 현재 팀원은 계속 본다.
	 */
	public boolean isPubliclyVisible() { return isPublic && !isRestricted; }
	public void recordView() { viewCount++; }

	public int getTotalCapacity() {
		return 1 + recruitmentSlots.stream().mapToInt(ProjectRecruitmentSlot::capacity).sum();
	}
}

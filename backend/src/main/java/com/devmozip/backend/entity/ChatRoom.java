package com.devmozip.backend.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 대화방. 1:1 방과 프로젝트 단톡방이 함께 쓴다. */
@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom {
	public enum Type { DIRECT, PROJECT }
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
	private Type type;
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "project_id", unique = true)
	private Project project;
	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	public static ChatRoom direct() {
		return new ChatRoom(Type.DIRECT, null);
	}
	public static ChatRoom project(Project project) {
		return new ChatRoom(Type.PROJECT, project);
	}
	/** 방이 없는 프로젝트를 삭제할 때 쓴다. 이 행이 있으면 다음 접근 때 새 방을 만들지 않는다. */
	public static ChatRoom deletedProject(Project project, LocalDateTime now) {
		ChatRoom room = project(project);
		room.deletedAt = now;
		return room;
	}
	private ChatRoom(Type type, Project project) {
		this.type = type;
		this.project = project;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	/** 논리 삭제. 이미 삭제된 방은 처음 삭제한 시각을 유지한다. */
	public void delete(LocalDateTime now) {
		if (deletedAt == null) {
			deletedAt = now;
		}
	}
}

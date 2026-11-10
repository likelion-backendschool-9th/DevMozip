package com.devmozip.backend.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.devmozip.backend.dto.admin.AdminPageResponse;
import com.devmozip.backend.dto.admin.AdminReportHandleRequest;
import com.devmozip.backend.dto.admin.AdminPartyResponse;
import com.devmozip.backend.dto.admin.AdminReportResponse;
import com.devmozip.backend.dto.admin.AdminReportTargetResponse;
import com.devmozip.backend.entity.Comment;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Post;
import com.devmozip.backend.entity.Project;
import com.devmozip.backend.entity.Report;
import com.devmozip.backend.repository.AdminReportQueryRepository;
import com.devmozip.backend.repository.CommentRepository;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.repository.PostRepository;
import com.devmozip.backend.repository.ProjectRepository;
import com.devmozip.backend.security.admin.AdminAccess;
import com.devmozip.backend.service.AdminPaging;
import com.devmozip.backend.service.AdminReportService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 관리자 신고 내역 처리 (docs/admin-plan.md 5.7). 처리 규칙은 기존 프로젝트 신고 관리(docs/project-reports.md)와 같다.
 * 대상 공고·글·댓글은 한 페이지의 ID를 모아 한 번씩 불러온다. 삭제된 대상도 기록으로 보여 준다.
 */
@Service
public class AdminReportServiceImpl implements AdminReportService {
	static final int MAX_RESOLUTION_LENGTH = 1000;
	static final Set<String> TYPES = Set.of("PROJECT", "MEMBER", "USER", "POST", "COMMENT");

	private final AdminAccess access;
	private final AdminReportQueryRepository reports;
	private final ProjectRepository projects;
	private final PostRepository posts;
	private final CommentRepository comments;
	private final MemberRepository members;
	private final Clock clock;

	public AdminReportServiceImpl(AdminAccess access, AdminReportQueryRepository reports, ProjectRepository projects, PostRepository posts,
		CommentRepository comments, MemberRepository members, @Qualifier("adminClock") Clock clock) {
		this.access = access;
		this.reports = reports;
		this.projects = projects;
		this.posts = posts;
		this.comments = comments;
		this.members = members;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public AdminPageResponse<AdminReportResponse> list(String type, String status, int page, int size) {
		access.require();
		String typeFilter = type == null || type.isBlank() ? null : type;
		if (typeFilter != null && !TYPES.contains(typeFilter)) throw badRequest("신고 종류를 확인해 주세요.");
		Report.Status statusFilter = AdminPaging.option(Report.Status.class, status, "처리 상태를 확인해 주세요.");
		Page<Report> rows = reports.search(statusFilter, typeFilter, AdminPaging.request(page, size));
		Lookup lookup = lookup(rows.getContent());
		return AdminPageResponse.of(rows, lookup::response);
	}

	@Override
	@Transactional
	public AdminReportResponse handle(long id, AdminReportHandleRequest request) {
		Member admin = access.require();
		Report report = reports.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "신고를 찾을 수 없습니다."));
		if (report.getStatus() == Report.Status.RESOLVED || report.getStatus() == Report.Status.REJECTED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리가 끝난 신고입니다.");
		}
		if (request.status() == Report.Status.AWATING) throw badRequest("접수 상태로 되돌릴 수 없습니다.");
		String note = request.resolution() == null ? "" : request.resolution().strip();
		if (request.status() != Report.Status.REVIEWING && note.isEmpty()) throw badRequest("처리 결과를 입력해 주세요.");
		if (note.length() > MAX_RESOLUTION_LENGTH) throw badRequest("처리 결과는 1,000자 이하로 입력해 주세요.");
		report.handle(request.status(), note, admin.getId(), LocalDateTime.now(clock));
		return lookup(List.of(report)).response(report);
	}

	static String typeOf(Report report) {
		if (report.getCommentId() != null) return "COMMENT";
		if (report.getPostId() != null) return "POST";
		if (report.getProjectId() != null) return report.getReportedUser() == null ? "PROJECT" : "MEMBER";
		return "USER";
	}

	private Lookup lookup(List<Report> rows) {
		return new Lookup(
			byId(projects.findAllById(ids(rows, Report::getProjectId)), Project::getId),
			byId(posts.findAllById(ids(rows, Report::getPostId)), Post::getId),
			byId(comments.findAllById(ids(rows, Report::getCommentId)), Comment::getId),
			byId(members.findAllById(ids(rows, Report::getHandledBy)), Member::getId));
	}

	private static List<Long> ids(Collection<Report> rows, Function<Report, Long> id) {
		return rows.stream().map(id).filter(Objects::nonNull).distinct().toList();
	}

	private static <T> Map<Long, T> byId(Collection<T> rows, Function<T, Long> id) {
		return rows.stream().collect(Collectors.toMap(id, Function.identity()));
	}

	private record Lookup(Map<Long, Project> projects, Map<Long, Post> posts, Map<Long, Comment> comments, Map<Long, Member> admins) {
		AdminReportResponse response(Report report) {
			String type = typeOf(report);
			Member handler = report.getHandledBy() == null ? null : admins.get(report.getHandledBy());
			return new AdminReportResponse(report.getId(), type, AdminPartyResponse.of(report.getReporter()), target(type, report),
				report.getReason(), report.getStatus().name(), report.getResolution(), handler == null ? null : handler.getNickname(),
				report.getHandledAt(), report.getCreatedAt());
		}

		private AdminReportTargetResponse target(String type, Report report) {
			return switch (type) {
				case "COMMENT" -> {
					Comment comment = comments.get(report.getCommentId());
					yield new AdminReportTargetResponse(null, null, comment == null ? null : AdminPartyResponse.of(comment.getAuthor()),
						comment == null ? null : comment.getPost().getId(), report.getCommentId(),
						comment == null ? "삭제된 댓글" : comment.getContent(), comment == null || comment.getDeletedAt() != null);
				}
				case "POST" -> {
					Post post = posts.get(report.getPostId());
					String content = post == null ? "삭제된 게시글" : post.getTitle() != null ? post.getTitle() : post.getContent();
					yield new AdminReportTargetResponse(null, null, post == null ? null : AdminPartyResponse.of(post.getAuthor()),
						report.getPostId(), null, content, post == null || post.isDeleted());
				}
				case "PROJECT", "MEMBER" -> {
					Project project = projects.get(report.getProjectId());
					yield new AdminReportTargetResponse(report.getProjectId(), project == null ? "삭제된 프로젝트" : project.getTitle(),
						AdminPartyResponse.of(report.getReportedUser()), null, null, null, project == null || project.getDeletedAt() != null);
				}
				default -> new AdminReportTargetResponse(null, null, AdminPartyResponse.of(report.getReportedUser()), null, null, null, false);
			};
		}
	}

	private static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}
}

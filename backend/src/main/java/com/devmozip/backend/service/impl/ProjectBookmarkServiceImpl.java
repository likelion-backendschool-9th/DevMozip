package com.devmozip.backend.service.impl;

import java.time.*;
import java.util.List;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.entity.*;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import com.devmozip.backend.service.ProjectBookmarkService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional(readOnly=true)
public class ProjectBookmarkServiceImpl implements ProjectBookmarkService {
	private final BookmarkRepository bookmarks;
	private final ProjectRepository projects;
	private final ProjectMemberRepository members;
	private final ProjectCurrentUserProvider currentUser;
	private final Clock clock;
	public ProjectBookmarkServiceImpl(BookmarkRepository bookmarks,ProjectRepository projects,ProjectMemberRepository members,ProjectCurrentUserProvider currentUser,@Qualifier("projectClock") Clock clock){
		this.bookmarks=bookmarks;this.projects=projects;this.members=members;this.currentUser=currentUser;this.clock=clock;
	}
	@Override @Transactional
	public ProjectBookmarkResponse save(long projectId){
		long userId=currentUser.getCurrentUserId();validate(projectId);
		// 공고 수정/삭제 및 동시 저장과 같은 프로젝트 잠금을 사용한다.
		var project=projects.findLiveForUpdate(projectId).orElseThrow(this::notFound);
		if(!project.isPublic()&&!members.existsByProjectIdAndUserIdAndStatus(projectId,userId,ProjectMember.Status.ACTIVE))throw notFound();
		if(!bookmarks.existsByUserIdAndProjectId(userId,projectId))bookmarks.saveAndFlush(new Bookmark(userId,project,LocalDateTime.now(clock)));
		return new ProjectBookmarkResponse(projectId,true);
	}
	@Override @Transactional
	public ProjectBookmarkResponse remove(long projectId){
		long userId=currentUser.getCurrentUserId();validate(projectId);
		// 열람 권한을 잃거나 공고가 삭제되어도 본인의 저장 기록은 정리할 수 있다.
		bookmarks.removeProject(userId,projectId);return new ProjectBookmarkResponse(projectId,false);
	}
	@Override public List<ProjectListItemResponse> list(){
		long userId=currentUser.getCurrentUserId();var today=LocalDate.now(clock);
		return bookmarks.visibleProjects(userId,ProjectMember.Status.ACTIVE).stream().map(b->{
			var project=b.getProject();var member=members.findByProjectIdAndUserIdAndStatus(project.getId(),userId,ProjectMember.Status.ACTIVE);
			return ProjectListItemResponse.from(project,members.countByProjectIdAndStatus(project.getId(),ProjectMember.Status.ACTIVE),member.map(m->m.getRole().name()).orElse(null),today);
		}).toList();
	}
	private void validate(long id){if(id<=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"프로젝트 ID를 확인해 주세요.");}
	private ResponseStatusException notFound(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"프로젝트가 없거나 접근할 수 없습니다.");}
}

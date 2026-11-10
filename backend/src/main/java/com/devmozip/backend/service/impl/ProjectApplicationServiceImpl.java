package com.devmozip.backend.service.impl;

import java.time.*;
import java.util.*;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.entity.*;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.event.*;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import com.devmozip.backend.service.*;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly=true)
public class ProjectApplicationServiceImpl implements ProjectApplicationService {
	private final ProjectRepository projects;
	private final ProjectMemberRepository members;
	private final ProjectApplicationRepository applications;
	private final ProjectApplicationPortfolioRepository links;
	private final MemberRepository users;
	private final ProjectCurrentUserProvider currentUser;
	private final ProjectPortfolioAccess portfolios;
	private final Clock clock;
	private final Validator validator;
	private final ApplicationEventPublisher events;
	public ProjectApplicationServiceImpl(ProjectRepository projects,ProjectMemberRepository members,ProjectApplicationRepository applications,
		ProjectApplicationPortfolioRepository links,MemberRepository users,ProjectCurrentUserProvider currentUser,ProjectPortfolioAccess portfolios,
		@Qualifier("projectClock") Clock clock,Validator validator,ApplicationEventPublisher events){
		this.projects=projects;this.members=members;this.applications=applications;this.links=links;this.users=users;
		this.currentUser=currentUser;this.portfolios=portfolios;this.clock=clock;this.validator=validator;this.events=events;
	}
	@Override @Transactional
	public ProjectApplicationResponse apply(long id,CreateProjectApplicationRequest request){
		long user=currentUser.getCurrentUserId();var project=locked(id);
		if(request==null||!validator.validate(request).isEmpty())throw bad("지원 직군·포트폴리오 1~3개·지원 멘트 10~1500자를 확인해 주세요.");
		if(new HashSet<>(request.portfolioIds()).size()!=request.portfolioIds().size())throw bad("포트폴리오를 중복 제출할 수 없습니다.");
		String unavailable=unavailable(project,user);
		if(unavailable!=null)throw conflict(unavailable);
		var slot=slot(project,request.jobRole());
		if(filled(id,slot.roleCode())>=slot.capacity())throw conflict("해당 직군의 정원이 찼습니다.");
		if(!portfolios.available())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"포트폴리오 연동 준비 후 지원할 수 있습니다.");
		if(!portfolios.ownsAll(user,request.portfolioIds()))throw forbidden("본인의 존재하는 포트폴리오만 제출할 수 있습니다.");
		var now=LocalDateTime.now(clock);
		var application=applications.saveAndFlush(new ProjectApplication(project,user,slot.roleCode(),request.introduction().strip(),now));
		for(long portfolioId:request.portfolioIds())links.save(new ProjectApplicationPortfolio(application,portfolioId,now));
		links.flush();emit(application,ProjectApplicationChanged.Type.SUBMITTED,false,now);
		return response(application);
	}
	@Override public List<ProjectApplicationResponse> list(long id,ProjectApplication.Status status){
		var project=live(id);requireHost(project,currentUser.getCurrentUserId());
		return applications.findByProjectIdOrderByAppliedAtDescIdDesc(id).stream().filter(a->status==null||a.getStatus()==status).map(this::response).toList();
	}
	@Override public ProjectApplicationOptions options(long id){
		var project=live(id);long user=currentUser.getCurrentUserId();
		var own=applications.findByProjectIdAndApplicantMemberId(id,user);
		if(!project.isPublic()&&own.isEmpty()&&!members.existsByProjectIdAndUserIdAndStatus(id,user,ProjectMember.Status.ACTIVE))throw missing();
		boolean available=portfolios.available();String reason=unavailable(project,user);
		var roles=project.getRecruitmentSlots().stream().map(s->new ProjectApplicationOptions.Role(s.roleCode(),s.capacity(),filled(id,s.roleCode()),s.minimumYears())).toList();
		if(reason==null&&roles.stream().noneMatch(s->s.filled()<s.capacity()))reason="모집 정원이 찼습니다.";
		if(reason==null&&!available)reason="포트폴리오 연동 준비 후 지원할 수 있습니다.";
		return new ProjectApplicationOptions(roles,available?portfolios.ownedBy(user):List.of(),available,own.map(this::response).orElse(null),reason==null,reason);
	}
	@Override @Transactional
	public ProjectApplicationResponse update(long id,long applicationId,UpdateProjectApplicationRequest request){
		long user=currentUser.getCurrentUserId();var project=locked(id);
		if(request==null||!validator.validate(request).isEmpty()||request.status()==ProjectApplication.Status.PENDING)throw bad("승인·거절·취소 명령을 확인해 주세요.");
		if(request.status()!=ProjectApplication.Status.CANCELLED)requireHost(project,user);
		var application=applications.findByIdAndProjectId(applicationId,id).orElseThrow(this::missing);
		if(request.status()==ProjectApplication.Status.CANCELLED&&application.getApplicantMemberId()!=user)throw forbidden("본인의 지원만 취소할 수 있습니다.");
		if(project.getStatus()==ProjectStatus.COMPLETED)throw conflict("종료된 프로젝트의 지원 상태를 변경할 수 없습니다.");
		if(request.status()!=ProjectApplication.Status.REJECTED&&request.rejectionReason()!=null&&!request.rejectionReason().isBlank())throw bad("거절 사유는 거절할 때만 입력해 주세요.");
		var now=LocalDateTime.now(clock);boolean penalty=false;ProjectApplicationChanged.Type type;
		switch(request.status()){
			case APPROVED -> {
				requirePending(application);
				if(project.getStatus()!=ProjectStatus.RECRUITING&&project.getStatus()!=ProjectStatus.RECRUITMENT_COMPLETED)throw conflict("프로젝트 시작 전에만 승인할 수 있습니다.");
				var slot=slot(project,application.getJobRole());
				if(filled(id,slot.roleCode())>=slot.capacity())throw conflict("해당 직군의 정원이 찼습니다.");
				if(members.existsByProjectIdAndUserId(id,application.getApplicantMemberId()))throw conflict("이미 참여 기록이 있는 회원입니다.");
				members.saveAndFlush(ProjectMember.participant(project,application.getApplicantMemberId(),slot.roleCode(),now));
				application.approve(now);type=ProjectApplicationChanged.Type.APPROVED;
			}
			case REJECTED -> {
				requirePending(application);String reason=request.rejectionReason();
				if(reason==null||reason.isBlank()||reason.strip().length()>200||reason.chars().anyMatch(Character::isISOControl))throw bad("거절 사유를 한 줄 1~200자로 입력해 주세요.");
				application.reject(reason.strip(),now);type=ProjectApplicationChanged.Type.REJECTED;
			}
			case CANCELLED -> {
				if(application.getStatus()!=ProjectApplication.Status.PENDING&&application.getStatus()!=ProjectApplication.Status.APPROVED)throw conflict("이미 처리된 지원은 취소할 수 없습니다. 재지원은 허용하지 않습니다.");
				penalty=late(application);
				if(penalty&&!Boolean.TRUE.equals(request.acknowledgePenalty()))throw bad("확정 참여 취소의 신뢰 점수 안내를 확인해 주세요.");
				if(application.getStatus()==ProjectApplication.Status.APPROVED){
					var member=members.findByProjectIdAndUserIdAndStatus(id,user,ProjectMember.Status.ACTIVE).orElseThrow(()->conflict("현재 참여 상태를 확인해 주세요."));
					if(member.getRole()!=ProjectMember.Role.PARTICIPANT)throw conflict("주최자는 지원 취소로 탈퇴할 수 없습니다.");
					member.leave(now);members.flush();recalculateEnd(project,user,now);
				}
				application.cancel(now);type=ProjectApplicationChanged.Type.CANCELLED;
			}
			default -> throw bad("지원 상태를 확인해 주세요.");
		}
		applications.flush();projects.flush();emit(application,type,penalty,now);return response(application);
	}
	@Override public List<ProjectMemberResponse> members(long id,String jobRole){
		var project=live(id);long user=currentUser.getCurrentUserId();
		if(!members.existsByProjectIdAndUserIdAndStatus(id,user,ProjectMember.Status.ACTIVE))throw forbidden("현재 팀원만 팀원 목록을 조회할 수 있습니다.");
		String key=jobRole==null||jobRole.isBlank()?null:ProjectRoleValues.key(jobRole);
		return members.findByProjectId(id).stream().filter(m->m.getStatus()==ProjectMember.Status.ACTIVE)
			.filter(m->key==null||m.getJobRole()!=null&&ProjectRoleValues.key(m.getJobRole()).equals(key)).map(m->{
				var profile=users.findById(m.getUserId());return new ProjectMemberResponse(m.getId(),m.getUserId(),profile.map(Member::getNickname).orElse(null),profile.map(Member::getTag).orElse(null),m.getRole().name(),m.getJobRole(),m.getStatus().name(),m.getJoinedAt());
			}).toList();
	}
	private ProjectApplicationResponse response(ProjectApplication a){
		var user=users.findById(a.getApplicantMemberId());
		return new ProjectApplicationResponse(a.getId(),a.getProject().getId(),a.getApplicantMemberId(),user.map(Member::getNickname).orElse(null),user.map(Member::getTag).orElse(null),a.getJobRole(),a.getIntroduction(),a.getStatus(),a.getRejectionReason(),links.findByApplicationIdOrderByIdAsc(a.getId()).stream().map(ProjectApplicationPortfolio::getPortfolioId).filter(Objects::nonNull).toList(),a.getAppliedAt(),a.getProcessedAt(),a.getCancelledAt(),late(a),a.getProject().getRecruitmentEnd().minusDays(7));
	}
	private boolean late(ProjectApplication a){return a.getStatus()==ProjectApplication.Status.APPROVED&&LocalDate.now(clock).isAfter(a.getProject().getRecruitmentEnd().minusDays(7));}
	private String unavailable(Project p,long user){
		if(p.getHostMemberId()==user)return "주최자는 자신의 공고에 지원할 수 없습니다.";
		if(applications.existsByProjectIdAndApplicantMemberId(p.getId(),user))return "이미 지원 기록이 있습니다. 취소·거절 후 재지원은 허용하지 않습니다.";
		if(members.existsByProjectIdAndUserId(p.getId(),user))return "이미 참여 기록이 있는 프로젝트입니다.";
		if(!p.isPublic())return "비공개 프로젝트에는 지원할 수 없습니다.";
		var today=LocalDate.now(clock);
		if(p.getStatus()!=ProjectStatus.RECRUITING||today.isBefore(p.getRecruitmentStart())||today.isAfter(p.getRecruitmentEnd()))return "현재 지원을 받지 않는 프로젝트입니다.";
		return null;
	}
	private long filled(long id,String jobRole){String key=ProjectRoleValues.key(jobRole);return members.findByProjectId(id).stream().filter(m->m.getStatus()==ProjectMember.Status.ACTIVE&&m.getRole()==ProjectMember.Role.PARTICIPANT&&m.getJobRole()!=null&&ProjectRoleValues.key(m.getJobRole()).equals(key)).count();}
	private ProjectRecruitmentSlot slot(Project p,String role){String key=ProjectRoleValues.key(role);return p.getRecruitmentSlots().stream().filter(s->ProjectRoleValues.key(s.roleCode()).equals(key)).findFirst().orElseThrow(()->conflict("프로젝트에 등록된 모집 직군을 선택해 주세요."));}
	private void recalculateEnd(Project p,long user,LocalDateTime now){
		if(p.getStatus()!=ProjectStatus.IN_PROGRESS)return;
		long active=members.countByProjectIdAndStatus(p.getId(),ProjectMember.Status.ACTIVE),requested=members.countByProjectIdAndStatusAndEndRequestedAtIsNotNull(p.getId(),ProjectMember.Status.ACTIVE);
		if(active>0&&requested>active/2){p.changeStatus(ProjectStatus.COMPLETED,now);events.publishEvent(new ProjectLifecycleChanged(ProjectLifecycleChanged.Type.COMPLETED,p.getId(),user,ProjectStatus.IN_PROGRESS,p.getStatus(),p.getRecruitmentEnd(),p.getRecruitmentEnd(),p.getDevelopmentEnd(),p.getDevelopmentEnd(),now));}
	}
	private void emit(ProjectApplication a,ProjectApplicationChanged.Type type,boolean penalty,LocalDateTime now){events.publishEvent(new ProjectApplicationChanged(type,a.getProject().getId(),a.getId(),a.getApplicantMemberId(),a.getProject().getHostMemberId(),a.getJobRole(),penalty,"project-application:"+a.getId()+":"+type,now));}
	private void requireHost(Project p,long user){if(p.getHostMemberId()!=user||!members.existsByProjectIdAndUserIdAndStatus(p.getId(),user,ProjectMember.Status.ACTIVE))throw forbidden("현재 주최자만 지원자를 관리할 수 있습니다.");}
	private void requirePending(ProjectApplication a){if(a.getStatus()!=ProjectApplication.Status.PENDING)throw conflict("대기 중인 지원만 승인·거절할 수 있습니다.");}
	private Project live(long id){if(id<=0)throw bad("프로젝트 ID를 확인해 주세요.");return projects.findByIdAndDeletedAtIsNull(id).orElseThrow(this::missing);}
	private Project locked(long id){if(id<=0)throw bad("프로젝트 ID를 확인해 주세요.");return projects.findLiveForUpdate(id).orElseThrow(this::missing);}
	private ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
	private ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
	private ResponseStatusException forbidden(String text){return new ResponseStatusException(HttpStatus.FORBIDDEN,text);}
	private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"프로젝트 또는 지원서를 찾을 수 없습니다.");}
}

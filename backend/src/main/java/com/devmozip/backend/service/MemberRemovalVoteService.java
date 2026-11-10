package com.devmozip.backend.service;

import java.time.*;
import java.util.*;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.dto.message.ProjectMembersChangedFrame;
import com.devmozip.backend.entity.*;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.event.*;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import com.devmozip.backend.websocket.MessengerSignal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional
public class MemberRemovalVoteService {
	private static final List<String> REASONS=List.of("반복적인 욕설 또는 비방","협업 규칙 위반","지속적인 활동 방해","기타");
	private final ProjectRepository projects;private final ProjectMemberRepository members;private final MemberRepository users;
	private final MemberRemovalVoteRepository votes;private final MemberRemovalBallotRepository ballots;
	private final ProjectCurrentUserProvider identity;private final ProjectChatService chat;private final Clock clock;private final ApplicationEventPublisher events;
	public MemberRemovalVoteService(ProjectRepository projects,ProjectMemberRepository members,MemberRepository users,MemberRemovalVoteRepository votes,
		MemberRemovalBallotRepository ballots,ProjectCurrentUserProvider identity,ProjectChatService chat,@Qualifier("projectClock") Clock clock,ApplicationEventPublisher events){
		this.projects=projects;this.members=members;this.users=users;this.votes=votes;this.ballots=ballots;this.identity=identity;this.chat=chat;this.clock=clock;this.events=events;
	}
	public List<MemberRemovalVoteResponse> list(long projectId){
		var p=locked(projectId);long user=identity.getCurrentUserId();var roster=roster(p,user);sync(p,roster);
		return votes.findTop30ByProjectIdOrderByIdDesc(projectId).stream().map(v->response(v,user)).toList();
	}
	public MemberRemovalVoteResponse start(long projectId,CreateMemberRemovalVoteRequest request){
		var p=locked(projectId);long user=identity.getCurrentUserId();var roster=roster(p,user);sync(p,roster);requireOpen(p);
		if(!votes.findByProjectIdAndStatus(projectId,MemberRemovalVote.Status.ACTIVE).isEmpty())throw error(409,"진행 중인 강퇴 투표가 있습니다.");
		var proposer=roster.stream().filter(m->m.getUserId()==user).findFirst().orElseThrow(()->error(403,"현재 팀원만 투표를 요청할 수 있습니다."));
		var target=roster.stream().filter(m->m.getId()==request.projectMemberId()).findFirst().orElseThrow(()->error(400,"현재 채팅방 팀원을 선택해 주세요."));
		if(target.getRole()==ProjectMember.Role.HOST||target.getUserId()==user)throw error(400,"본인과 주최자는 강퇴 대상이 될 수 없습니다.");
		if(!REASONS.contains(request.reason()))throw error(400,"등록된 강퇴 사유를 선택해 주세요.");
		var now=now();var vote=votes.saveAndFlush(new MemberRemovalVote(p,proposer,target,request.reason(),roster.size()-1,now));
		for(var m:roster)if(!m.getId().equals(target.getId()))ballots.save(new MemberRemovalBallot(vote,m,now));
		ballots.flush();signal(vote);return response(vote,user);
	}
	public MemberRemovalVoteResponse cast(long projectId,long voteId,CastMemberRemovalBallotRequest request){
		var p=locked(projectId);long user=identity.getCurrentUserId();var roster=roster(p,user);sync(p,roster);
		var vote=votes.findByIdAndProjectId(voteId,projectId).orElseThrow(()->error(404,"이 프로젝트의 투표가 아닙니다."));
		var rows=ballots.findByVoteIdOrderByIdAsc(voteId);var mine=rows.stream().filter(b->b.getVoter().getUserId()==user).findFirst().orElseThrow(()->error(403,"강퇴 대상자는 투표할 수 없습니다."));
		if(vote.getStatus()!=MemberRemovalVote.Status.ACTIVE)return response(vote,user);
		if(mine.getVotedAt()!=null)throw error(409,"한 번만 투표할 수 있습니다.");
		mine.cast(request.agree(),now());ballots.flush();long yes=rows.stream().filter(b->Boolean.TRUE.equals(b.getAgree())).count();
		if(yes>vote.getEligibleVoterCount()/2){
			vote.getTarget().kick(now());members.flush();chat.excludeMember(projectId,vote.getTarget().getUserId());resolve(vote,MemberRemovalVote.Status.PASSED);
			recalculateEnd(p,user);
		}else if(rows.stream().allMatch(b->b.getVotedAt()!=null))resolve(vote,MemberRemovalVote.Status.REJECTED);
		else signal(vote);
		return response(vote,user);
	}
	private List<ProjectMember> roster(Project p,long user){
		var detail=chat.getProjectChat(user,p.getId(),null,null,1);
		var ids=detail.members().stream().filter(m->m.leftAt()==null).map(m->m.projectMemberId()).collect(java.util.stream.Collectors.toSet());
		return members.findByProjectId(p.getId()).stream().filter(m->m.getStatus()==ProjectMember.Status.ACTIVE&&ids.contains(m.getId())).toList();
	}
	private void sync(Project p,List<ProjectMember> roster){
		for(var v:votes.findByProjectIdAndStatus(p.getId(),MemberRemovalVote.Status.ACTIVE)){
			var expected=ballots.findByVoteIdOrderByIdAsc(v.getId()).stream().map(b->b.getVoter().getId()).collect(java.util.stream.Collectors.toSet());
			var current=roster.stream().filter(m->!m.getId().equals(v.getTarget().getId())).map(ProjectMember::getId).collect(java.util.stream.Collectors.toSet());
			if(p.getStatus()==ProjectStatus.COMPLETED||roster.stream().noneMatch(m->m.getId().equals(v.getTarget().getId()))||!expected.equals(current))resolve(v,MemberRemovalVote.Status.CANCELLED);
			else if(!now().isBefore(v.getExpiresAt()))resolve(v,MemberRemovalVote.Status.EXPIRED);
		}
	}
	@EventListener @Transactional(propagation=Propagation.MANDATORY)
	public void completed(ProjectLifecycleChanged event){if(event.type()==ProjectLifecycleChanged.Type.COMPLETED)for(var v:votes.findByProjectIdAndStatus(event.projectId(),MemberRemovalVote.Status.ACTIVE))resolve(v,MemberRemovalVote.Status.CANCELLED);}
	private void resolve(MemberRemovalVote v,MemberRemovalVote.Status status){
		v.resolve(status,now());votes.flush();var recipients=members.findByProjectId(v.getProject().getId()).stream().map(ProjectMember::getUserId).distinct().toList();
		events.publishEvent(new ProjectMemberRemovalResolved(v.getProject().getId(),v.getId(),v.getTarget().getUserId(),status.name(),recipients,"project-removal:"+v.getId()+":"+status,now()));signal(v);
	}
	private void signal(MemberRemovalVote v){
		var recipients=members.findByProjectId(v.getProject().getId()).stream().filter(m->m.getStatus()==ProjectMember.Status.ACTIVE||m.getId().equals(v.getTarget().getId())).map(ProjectMember::getUserId).distinct().toList();
		events.publishEvent(new MessengerSignal(recipients,new ProjectMembersChangedFrame(v.getProject().getId(),v.getId(),v.getStatus().name())));
	}
	private MemberRemovalVoteResponse response(MemberRemovalVote v,long user){
		var rows=ballots.findByVoteIdOrderByIdAsc(v.getId());var mine=rows.stream().filter(b->b.getVoter().getUserId()==user).findFirst();
		int yes=(int)rows.stream().filter(b->Boolean.TRUE.equals(b.getAgree())).count(),no=(int)rows.stream().filter(b->Boolean.FALSE.equals(b.getAgree())).count();
		return new MemberRemovalVoteResponse(v.getId(),v.getProject().getId(),v.getTarget().getId(),users.findById(v.getTarget().getUserId()).map(Member::getNickname).orElse("팀원"),v.getReason(),v.getStatus().name(),yes,no,v.getEligibleVoterCount(),v.getEligibleVoterCount()/2+1,mine.map(MemberRemovalBallot::getAgree).orElse(null),v.getStatus()==MemberRemovalVote.Status.ACTIVE&&mine.isPresent()&&mine.get().getVotedAt()==null,v.getCreatedAt(),v.getExpiresAt(),v.getResolvedAt());
	}
	private void recalculateEnd(Project p,long user){
		if(p.getStatus()!=ProjectStatus.IN_PROGRESS)return;long active=members.countByProjectIdAndStatus(p.getId(),ProjectMember.Status.ACTIVE);
		if(members.countByProjectIdAndStatusAndEndRequestedAtIsNotNull(p.getId(),ProjectMember.Status.ACTIVE)>active/2){var now=now();p.changeStatus(ProjectStatus.COMPLETED,now);events.publishEvent(new ProjectLifecycleChanged(ProjectLifecycleChanged.Type.COMPLETED,p.getId(),user,ProjectStatus.IN_PROGRESS,p.getStatus(),p.getRecruitmentEnd(),p.getRecruitmentEnd(),p.getDevelopmentEnd(),p.getDevelopmentEnd(),now));}
	}
	private Project locked(long id){return projects.findLiveForUpdate(id).orElseThrow(()->error(404,"프로젝트가 없습니다."));}
	private void requireOpen(Project p){if(p.getStatus()==ProjectStatus.COMPLETED)throw error(409,"종료된 프로젝트에서는 투표할 수 없습니다.");}
	private LocalDateTime now(){return LocalDateTime.now(clock);}
	private ResponseStatusException error(int code,String message){return new ResponseStatusException(HttpStatus.valueOf(code),message);}
}

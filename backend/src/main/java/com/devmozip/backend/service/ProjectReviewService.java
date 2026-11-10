package com.devmozip.backend.service;
import java.time.*;
import java.security.*;
import java.util.*;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.entity.*;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.event.*;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@Service @Transactional(readOnly=true)
public class ProjectReviewService {
	private final ProjectRepository projects;private final ProjectMemberRepository members;private final ProjectReviewRepository reviews;private final MemberRepository users;
	private final ProjectCurrentUserProvider identity;private final Clock clock;private final Validator validator;private final ApplicationEventPublisher events;
	private final ReviewKeywordRepository keywordRepository;private final ReviewKeywordSelectionRepository selections;
	private final SecureRandom random=new SecureRandom();
	public ProjectReviewService(ProjectRepository projects,ProjectMemberRepository members,ProjectReviewRepository reviews,MemberRepository users,ProjectCurrentUserProvider identity,@Qualifier("projectClock") Clock clock,Validator validator,ApplicationEventPublisher events,ReviewKeywordRepository keywordRepository,ReviewKeywordSelectionRepository selections){this.keywordRepository=keywordRepository;this.selections=selections;this.projects=projects;this.members=members;this.reviews=reviews;this.users=users;this.identity=identity;this.clock=clock;this.validator=validator;this.events=events;}
	@EventListener @Transactional(propagation=Propagation.MANDATORY)
	public void completed(ProjectLifecycleChanged event){if(event.type()!=ProjectLifecycleChanged.Type.COMPLETED)return;var p=locked(event.projectId());if(p.getStatus()!=ProjectStatus.COMPLETED||p.getCompletedAt()==null)return;
	 // 동일 프로젝트 잠금에서 생성 여부를 검사하여 종료 재처리가 기존 대상을 늘리지 않는다.
	 if(!reviews.findByProjectIdOrderByIdAsc(p.getId()).isEmpty())return;
	 var team=members.findByProjectId(p.getId()).stream().filter(m->m.getStatus()==ProjectMember.Status.ACTIVE).toList();
	 if(team.size()<2)return;
	 var now=p.getCompletedAt();for(var author:team){String token=token();for(var target:team)if(!author.getId().equals(target.getId()))reviews.save(new ProjectReview(p,author,target,hash(token),now));
	  events.publishEvent(new ProjectReviewNotificationEvent(p.getId(),author.getUserId(),href(p.getId(),token),false,"project-review:"+p.getId()+":"+author.getId()+":initial",now.plusDays(30)));}
	 reviews.flush();
	}
	public ProjectReviewResponse mine(long id){var p=live(id);var rows=own(p,identity.getCurrentUserId());var selected=selectedByReview(rows);var now=LocalDateTime.now(clock);return new ProjectReviewResponse(id,p.getTitle(),p.getCompletedAt(),rows.getFirst().getExpiresAt(),rows.stream().allMatch(r->r.getStatus()==ProjectReview.Status.SUBMITTED),!now.isBefore(rows.getFirst().getExpiresAt()),keywordRepository.findByActiveTrueOrderByIdAsc().stream().map(ReviewKeyword::getName).toList(),rows.stream().map(r->{var u=users.findById(r.getReviewee().getUserId());return new ProjectReviewResponse.Target(r.getReviewee().getId(),r.getReviewee().getUserId(),u.map(Member::getNickname).orElse("협업자"),u.map(Member::getTag).orElse(null),r.getStatus().name(),r.getRating(),selected.getOrDefault(r.getId(),List.of()));}).toList());}
	@Transactional public ProjectReviewLinkResponse link(long id){long user=identity.getCurrentUserId();var p=locked(id);var rows=own(p,user);editable(rows);String token=token();var now=LocalDateTime.now(clock);rows.forEach(r->r.rotate(hash(token),now));reviews.flush();return new ProjectReviewLinkResponse(href(id,token),rows.getFirst().getExpiresAt());}
	@Transactional public ProjectReviewResponse submit(long id,SubmitProjectReviewsRequest request){long user=identity.getCurrentUserId();var p=locked(id);var rows=own(p,user);editable(rows);
	 if(request==null||!validator.validate(request).isEmpty())throw error(400,"팀원별 별점 1~5점·키워드 0~5개·리뷰 링크를 확인해 주세요.");
	 var pending=rows.stream().filter(r->r.getStatus()==ProjectReview.Status.PENDING).toList();
	 var submittedHash=hash(request.token()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
	 if(pending.stream().anyMatch(r->!MessageDigest.isEqual(r.getTokenHash().getBytes(java.nio.charset.StandardCharsets.UTF_8),submittedHash)))throw error(403,"본인에게 발급된 최신 리뷰 링크가 아닙니다.");
	 var entries=request.entries();var targetIds=new HashSet<Long>();for(var e:entries)if(!targetIds.add(e.targetMemberId()))throw error(400,"대상을 중복 평가할 수 없습니다.");
	 if(entries.size()!=pending.size()||!targetIds.equals(new HashSet<>(pending.stream().map(r->r.getReviewee().getId()).toList())))throw error(400,"미제출 팀원 전체를 평가해 주세요.");
	 for(var e:entries)if(new HashSet<>(e.keywords()).size()!=e.keywords().size())throw error(400,"리뷰 키워드를 중복 선택할 수 없습니다.");
	 var chosen=entries.stream().flatMap(e->e.keywords().stream()).collect(java.util.stream.Collectors.toSet());
	 var resolved=keywordRepository.findByNameInAndActiveTrue(chosen).stream().collect(java.util.stream.Collectors.toMap(ReviewKeyword::getName,k->k));
	 if(resolved.size()!=chosen.size())throw error(400,"등록된 활성 키워드에서 최대 5개까지 선택해 주세요.");
	 var now=LocalDateTime.now(clock);for(var row:pending){var e=entries.stream().filter(x->x.targetMemberId()==row.getReviewee().getId()).findFirst().orElseThrow();row.submit(e.rating(),now);for(String name:e.keywords())selections.save(new ReviewKeywordSelection(row,resolved.get(name),now));}selections.flush();reviews.flush();return mine(id);
	}
	@Transactional public int remind(long id){long user=identity.getCurrentUserId();var p=locked(id);if(p.getHostMemberId()!=user)throw error(403,"주최자만 리뷰 재알림을 확인할 수 있습니다.");var now=LocalDateTime.now(clock);var groups=new LinkedHashMap<Long,List<ProjectReview>>();for(var row:reviews.findByProjectIdOrderByIdAsc(id))groups.computeIfAbsent(row.getReviewer().getId(),k->new ArrayList<>()).add(row);int count=0;
	 for(var group:groups.values()){var pending=group.stream().filter(r->r.getStatus()==ProjectReview.Status.PENDING).toList();if(pending.isEmpty())continue;var first=pending.getFirst();if(!now.isBefore(first.getExpiresAt()))continue;var last=pending.stream().map(r->r.getLastRemindedAt()==null?r.getCreatedAt():r.getLastRemindedAt()).max(LocalDateTime::compareTo).orElseThrow();if(now.isBefore(last.plusDays(3)))continue;
	  String token=token();for(var row:pending){row.rotate(hash(token),now);row.remind(now);}events.publishEvent(new ProjectReviewNotificationEvent(id,first.getReviewer().getUserId(),href(id,token),true,"project-review:"+id+":"+first.getReviewer().getId()+":reminder:"+now,first.getExpiresAt()));count++;
	 }reviews.flush();return count;
	}

 public java.util.List<ProjectReviewRequestStatusResponse> status(long id){long user=identity.getCurrentUserId();var p=live(id);if(p.getHostMemberId()!=user)throw error(403,"주최자만 요청 현황을 볼 수 있습니다.");var groups=new LinkedHashMap<Long,List<ProjectReview>>();for(var row:reviews.findByProjectIdOrderByIdAsc(id))groups.computeIfAbsent(row.getReviewer().getUserId(),k->new ArrayList<>()).add(row);return groups.entrySet().stream().map(e->new ProjectReviewRequestStatusResponse(e.getKey(),users.findById(e.getKey()).map(Member::getNickname).orElse("협업자"),e.getValue().size(),(int)e.getValue().stream().filter(r->r.getStatus()==ProjectReview.Status.SUBMITTED).count(),e.getValue().getFirst().getExpiresAt())).toList();}

	public ProjectReviewSummaryResponse received(long userId){if(userId<=0)throw error(400,"회원 ID를 확인해 주세요.");var records=reviews.findByRevieweeUserIdAndStatusOrderBySubmittedAtDesc(userId,ProjectReview.Status.SUBMITTED);var selected=selectedByReview(records);long[] bins=new long[5];Map<String,Long> counts=new TreeMap<>();double sum=0;var result=new ArrayList<ProjectReviewSummaryResponse.Received>();for(var row:records){if(row.getRating()==null||row.getRating()<1||row.getRating()>5)continue;bins[row.getRating()-1]++;sum+=row.getRating();for(String k:selected.getOrDefault(row.getId(),List.of()))counts.merge(k,1L,Long::sum);var author=users.findById(row.getReviewer().getUserId());result.add(new ProjectReviewSummaryResponse.Received(row.getId(),row.getProject().getId(),row.getProject().getTitle(),row.getReviewer().getUserId(),author.map(Member::getNickname).orElse("협업자"),author.map(Member::getTag).orElse(null),row.getRating(),selected.getOrDefault(row.getId(),List.of()),row.getSubmittedAt()));}return new ProjectReviewSummaryResponse(userId,result.size(),result.isEmpty()?0:sum/result.size(),Arrays.stream(bins).boxed().toList(),counts.entrySet().stream().map(e->new ProjectReviewSummaryResponse.Keyword(e.getKey(),e.getValue())).toList(),result);}

	private Map<Long,List<String>> selectedByReview(List<ProjectReview> rows){
		Map<Long,List<String>> result=new HashMap<>();
		if(rows.isEmpty())return result;
		for(var selection:selections.findWithKeywordsByReviewIds(rows.stream().map(ProjectReview::getId).toList()))
			result.computeIfAbsent(selection.getReview().getId(),id->new ArrayList<>()).add(selection.getKeyword().getName());
		return result;
	}

	private List<ProjectReview> own(Project p,long user){if(p.getStatus()!=ProjectStatus.COMPLETED)throw error(409,"종료된 프로젝트에서만 리뷰를 작성할 수 있습니다.");var rows=reviews.findByProjectIdOrderByIdAsc(p.getId()).stream().filter(r->r.getReviewer().getUserId()==user).toList();if(rows.isEmpty())throw error(403,"종료 당시 본인에게 발급된 리뷰 요청이 없습니다.");return rows;}
	private void editable(List<ProjectReview> rows){if(rows.stream().noneMatch(r->r.getStatus()==ProjectReview.Status.PENDING))throw error(409,"이미 전체 제출했습니다. 제출 후 수정할 수 없습니다.");var now=LocalDateTime.now(clock);if(!now.isBefore(rows.getFirst().getExpiresAt()))throw error(410,"리뷰 작성 기한 30일이 지났습니다.");if(now.isBefore(rows.getFirst().getCreatedAt()))throw error(409,"아직 리뷰 작성 기간이 아닙니다.");}
	private String href(long id,String token){return "/projects/"+id+"/reviews#token="+token;}
	private String token(){byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
	private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
	private Project live(long id){return projects.findByIdAndDeletedAtIsNull(id).orElseThrow(()->error(404,"프로젝트가 없습니다."));}
	private Project locked(long id){return projects.findLiveForUpdate(id).orElseThrow(()->error(404,"프로젝트가 없습니다."));}
	private ResponseStatusException error(int code,String message){return new ResponseStatusException(HttpStatus.valueOf(code),message);}
}

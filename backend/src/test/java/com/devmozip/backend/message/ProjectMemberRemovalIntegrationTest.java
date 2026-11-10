package com.devmozip.backend.message;

import java.time.*;
import java.util.*;
import java.util.concurrent.Callable;
import com.devmozip.backend.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.test.context.event.RecordApplicationEvents
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers(disabledWithoutDocker=true)
class ProjectMemberRemovalIntegrationTest extends MessengerApiTestSupport {
	@Container @ServiceConnection static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
	@org.springframework.beans.factory.annotation.Autowired org.springframework.test.context.event.ApplicationEvents events;
	@MockitoBean(name="projectClock") Clock projectClock;
	Member host,first,second,target;long project;
	@BeforeEach void fixture(){
		when(projectClock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));when(projectClock.instant()).thenReturn(NOW);
		host=member("host");first=member("first");second=member("second");target=member("target");
		var data=new ProjectChatTestData(jdbc);project=data.project(host.getId(),"강퇴 검증",LocalDate.of(2026,11,1));
		for(var u:List.of(first,second,target))data.approve(project,u.getId());
	}
	String base(){return "/api/v1/projects/"+project;}
	long mid(Member u){return jdbc.queryForObject("SELECT id FROM project_members WHERE project_id=? AND user_id=?",Long.class,project,u.getId());}
	long start() throws Exception{return json(postAs(host,base()+"/members",Map.of("projectMemberId",mid(target),"reason","협업 규칙 위반")).andExpect(status().isCreated())).get("id").asLong();}
	JsonNode cast(Member u,long id,boolean agree) throws Exception{return json(postAs(u,base()+"/kick-votes/"+id+"/ballots",Map.of("agree",agree)).andExpect(status().isOk()));}
	@Test void validatesEligibilityAndSnapshotsElectorate() throws Exception {
		for(var u:List.of(host,first))postAs(first,base()+"/members",Map.of("projectMemberId",mid(u),"reason","기타")).andExpect(status().isBadRequest());
		postAs(member("outsider"),base()+"/members",Map.of("projectMemberId",mid(target),"reason","기타")).andExpect(status().isNotFound());
		postAs(host,base()+"/members",Map.of("projectMemberId",mid(target),"reason","unknown")).andExpect(status().isBadRequest());
		long id=start();var row=json(getAs(host,base()+"/kick-votes").andExpect(status().isOk())).get(0);
		assertEquals(3,row.get("eligibleVoterCount").asInt());assertEquals(2,row.get("requiredCount").asInt());assertEquals(0,row.get("yes").asInt());assertEquals("2026-11-09T10:15:00",row.get("expiresAt").asText());
		postAs(target,base()+"/kick-votes/"+id+"/ballots",Map.of("agree",true)).andExpect(status().isForbidden());
		postAs(host,base()+"/kick-votes/"+id+"/ballots",Map.of()).andExpect(status().isBadRequest());
		postAs(host,base()+"/members",Map.of("projectMemberId",mid(first),"reason","기타")).andExpect(status().isConflict());
	}
	@Test void majorityPreservesHistoryAndMessagesButBlocksProjectChat() throws Exception {
		long id=start();postAs(target,"/api/v1/messages/projects/"+project,Map.of("body","기존 메시지 유지")).andExpect(status().isCreated());
		jdbc.update("UPDATE project_members SET end_requested_at=localtimestamp WHERE id=?",mid(target));
		assertEquals("ACTIVE",cast(host,id,true).get("status").asText());
		postAs(host,base()+"/kick-votes/"+id+"/ballots",Map.of("agree",false)).andExpect(status().isConflict());
		assertEquals("PASSED",cast(first,id,true).get("status").asText());
		var result=events.stream(com.devmozip.backend.event.ProjectMemberRemovalResolved.class).filter(e->e.voteId()==id).toList();
		assertEquals(1,result.size());assertEquals("PASSED",result.getFirst().status());assertTrue(result.getFirst().recipients().contains(target.getId()));
		assertTrue(events.stream(com.devmozip.backend.websocket.MessengerSignal.class).anyMatch(e->e.recipients().contains(target.getId())));
		assertEquals("KICKED",jdbc.queryForObject("SELECT status FROM project_members WHERE id=?",String.class,mid(target)));
		assertNotNull(jdbc.queryForObject("SELECT left_at FROM project_members WHERE id=?",LocalDateTime.class,mid(target)));
		assertNull(jdbc.queryForObject("SELECT end_requested_at FROM project_members WHERE id=?",LocalDateTime.class,mid(target)));
		assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=? AND status='ACTIVE'",Integer.class,project));
		assertEquals(4,jdbc.queryForObject("SELECT count(*) FROM project_members WHERE project_id=?",Integer.class,project));
		getAs(target,"/api/v1/messages/projects/"+project).andExpect(status().isNotFound());postAs(target,"/api/v1/messages/projects/"+project,Map.of("body","불가")).andExpect(status().isNotFound());
		assertTrue(json(getAs(host,"/api/v1/messages/projects/"+project).andExpect(status().isOk())).get("messages").toString().contains("기존 메시지 유지"));
		assertTrue(json(getAs(host,base()+"/members?includeInactive=true").andExpect(status().isOk())).toString().contains("KICKED"));getAs(first,base()+"/members?includeInactive=true").andExpect(status().isForbidden());
		assertEquals("PASSED",cast(second,id,false).get("status").asText());assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM member_removal_ballots WHERE vote_id=? AND voted_at IS NOT NULL",Integer.class,id));
	}
	@Test void rejectsUnanimousOppositionAndExpiresAtExactDeadline() throws Exception {
		long id=start();for(var u:List.of(host,first,second))cast(u,id,false);assertEquals("REJECTED",jdbc.queryForObject("SELECT status FROM member_removal_votes WHERE id=?",String.class,id));
		long expired=start();when(projectClock.instant()).thenReturn(NOW.plus(Duration.ofHours(72)));assertEquals("EXPIRED",cast(host,expired,true).get("status").asText());
		assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM member_removal_ballots WHERE vote_id=? AND voted_at IS NOT NULL",Integer.class,expired));
	}
	@Test void rosterChangeAndCompletionCancelVotes() throws Exception {
		long id=start();new ProjectChatTestData(jdbc).approve(project,member("joined").getId());assertEquals("CANCELLED",cast(host,id,true).get("status").asText());
		start();new ProjectChatTestData(jdbc).status(project,"COMPLETED");assertEquals("CANCELLED",json(getAs(host,base()+"/kick-votes").andExpect(status().isOk())).get(0).get("status").asText());
		postAs(host,base()+"/members",Map.of("projectMemberId",mid(target),"reason","기타")).andExpect(status().isConflict());
	}
	@Test void concurrentRequestsCannotCreateTwoVotesOrCastTwice() throws Exception {
		Callable<Integer> begin=()->postAs(host,base()+"/members",Map.of("projectMemberId",mid(target),"reason","기타")).andReturn().getResponse().getStatus();
		assertEquals(List.of(201,409),runTogether(List.of(begin,begin)).stream().sorted().toList());long id=jdbc.queryForObject("SELECT id FROM member_removal_votes WHERE project_id=?",Long.class,project);
		Callable<Integer> ballot=()->postAs(host,base()+"/kick-votes/"+id+"/ballots",Map.of("agree",true)).andReturn().getResponse().getStatus();assertEquals(List.of(200,409),runTogether(List.of(ballot,ballot)).stream().sorted().toList());
	}
	@Test void evenElectorateRequiresStrictlyMoreThanHalf() throws Exception {
		new ProjectChatTestData(jdbc).leave(project,second.getId());long id=start();
		assertEquals("ACTIVE",cast(host,id,true).get("status").asText());assertEquals("PASSED",cast(first,id,true).get("status").asText());
	}
	@Test void rejectsVotesFromAnotherProjectAndAnonymousRequests() throws Exception {
		long id=start();long other=new ProjectChatTestData(jdbc).project(host.getId(),"다른 프로젝트",LocalDate.of(2026,11,1));
		postAs(host,"/api/v1/projects/"+other+"/kick-votes/"+id+"/ballots",Map.of("agree",true)).andExpect(status().isNotFound());
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base()+"/kick-votes")).andExpect(status().isUnauthorized());
	}
	@Test void kickRecalculatesExistingEndRequestsAndExcludesRemovedMemberFromReviewRequests() throws Exception {
		new ProjectChatTestData(jdbc).status(project,"IN_PROGRESS");
		jdbc.update("UPDATE project_members SET end_requested_at=localtimestamp WHERE project_id=? AND user_id IN (?,?)",project,host.getId(),first.getId());
		long id=start();cast(host,id,true);cast(first,id,true);
		assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM projects WHERE id=?",String.class,project));
		assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM reviews WHERE project_id=? AND (reviewer_member_id=? OR reviewee_member_id=?)",Integer.class,project,mid(target),mid(target)));
	}

}

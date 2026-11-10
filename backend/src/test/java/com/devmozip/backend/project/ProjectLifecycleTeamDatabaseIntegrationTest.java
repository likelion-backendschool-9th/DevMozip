package com.devmozip.backend.project;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.LongConsumer;
import com.devmozip.backend.dto.project.*;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.repository.ProjectLifecycleRepository;
import com.devmozip.backend.security.project.ProjectCurrentUserProvider;
import com.devmozip.backend.service.ProjectService;
import com.devmozip.backend.service.ProjectLifecycleService;
import com.devmozip.backend.service.impl.ProjectLifecycleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static com.devmozip.backend.dto.project.ProjectLifecycleRequest.Action.*;

@SpringJUnitConfig({ProjectTeamDatabaseIntegrationTest.ProjectOnlyConfiguration.class,
	ProjectLifecycleTeamDatabaseIntegrationTest.LifecycleConfiguration.class})
@EnabledIfEnvironmentVariable(named="PROJECT_TEAM_DB_TEST",matches="true")
class ProjectLifecycleTeamDatabaseIntegrationTest {
	@Autowired ProjectService projects;
	@Autowired ProjectLifecycleService lifecycle;
	@Autowired JdbcTemplate jdbc;
	@Autowired PlatformTransactionManager transactions;
	@MockitoBean ProjectCurrentUserProvider user;
	private long actor=101;
	private final LocalDate today=LocalDate.now(ZoneId.of("Asia/Seoul"));
	@BeforeEach void actor(){actor=101;when(user.getCurrentUserId()).thenAnswer(invocation->actor);}
	private ProjectLifecycleRequest statusCommand(ProjectStatus status){return new ProjectLifecycleRequest(CHANGE_STATUS,status,null,null);}
	private ProjectLifecycleRequest command(ProjectLifecycleRequest.Action action){return new ProjectLifecycleRequest(action,null,null,null);}
	private void rollback(LongConsumer action){
		long[] id={0};
		try{
			new TransactionTemplate(transactions).executeWithoutResult(tx->{try{
				id[0]=projects.create(new CreateProjectRequest("db47_"+UUID.randomUUID(),"검증","#47 팀 DB 롤백 검증",null,
					"http://localhost:3000/portfolio-cover.svg",true,List.of(new CreateProjectRequest.RecruitmentSlot("BACKEND",5,0)),
					List.of("Java"),List.of("교육"),today,today.plusDays(7),today,today.plusDays(90))).id();
				action.accept(id[0]);
			}finally{tx.setRollbackOnly();}});
		}finally{
			if(id[0]>0)for(String table:List.of("projects","project_members","project_applications"))
				assertEquals(0L,jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE "+(table.equals("projects")?"id":"project_id")+"=?",Long.class,id[0]));
		}
	}
	@Test void storesTransitionsExtensionsAndSingleHostCompletionInTeamDb(){
		rollback(id->{
			lifecycle.update(id,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED));
			lifecycle.update(id,statusCommand(ProjectStatus.IN_PROGRESS));
			lifecycle.update(id,new ProjectLifecycleRequest(EXTEND_PERIOD,null,null,today.plusDays(120)));
			assertEquals(java.sql.Date.valueOf(today.plusDays(120)),jdbc.queryForObject("SELECT development_end FROM projects WHERE id=?",java.sql.Date.class,id));
			var response=lifecycle.update(id,command(REQUEST_END));
			assertEquals(ProjectStatus.COMPLETED,response.project().status());
			assertNotNull(jdbc.queryForObject("SELECT completed_at FROM projects WHERE id=?",java.sql.Timestamp.class,id));
			assertNotNull(jdbc.queryForObject("SELECT end_requested_at FROM project_members WHERE project_id=? AND user_id=101",java.sql.Timestamp.class,id));
		});
	}
	@Test void storesCancellableMemberRequestsAndCompletesAtThreeOfFour(){
		rollback(id->{
			for(long person=102;person<=104;person++)jdbc.update("INSERT INTO project_members(project_id,user_id,role,job_role,status,joined_at,created_at,updated_at) VALUES (?,?,'PARTICIPANT','BACKEND','ACTIVE',localtimestamp,localtimestamp,localtimestamp)",id,person);
			lifecycle.update(id,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED));lifecycle.update(id,statusCommand(ProjectStatus.IN_PROGRESS));
			lifecycle.update(id,command(REQUEST_END));assertEquals(0,lifecycle.update(id,command(CANCEL_END_REQUEST)).endRequestCount());
			lifecycle.update(id,command(REQUEST_END));actor=102;assertEquals(ProjectStatus.IN_PROGRESS,lifecycle.update(id,command(REQUEST_END)).project().status());
			actor=103;var response=lifecycle.update(id,command(REQUEST_END));assertEquals(ProjectStatus.COMPLETED,response.project().status());assertEquals(3,response.endRequestCount());
		});
	}
	@Test void readsRealApplicationTableToBlockStart(){
		rollback(id->{
			lifecycle.update(id,statusCommand(ProjectStatus.RECRUITMENT_COMPLETED));
			jdbc.update("INSERT INTO project_applications(project_id,applicant_member_id,job_role,status,applied_at,created_at,updated_at) VALUES (?,202,'BACKEND','PENDING',localtimestamp,localtimestamp,localtimestamp)",id);
			assertEquals(409,assertThrows(ResponseStatusException.class,()->lifecycle.update(id,statusCommand(ProjectStatus.IN_PROGRESS))).getStatusCode().value());
			jdbc.update("UPDATE project_applications SET status='REJECTED' WHERE project_id=?",id);
			assertEquals(ProjectStatus.IN_PROGRESS,lifecycle.update(id,statusCommand(ProjectStatus.IN_PROGRESS)).project().status());
		});
	}
	@TestConfiguration(proxyBeanMethods=false)
	@Import({ProjectLifecycleServiceImpl.class,ProjectLifecycleRepository.class})
	static class LifecycleConfiguration {}
}

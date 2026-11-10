package com.devmozip.backend.message;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Callable;
import com.devmozip.backend.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Testcontainers(disabledWithoutDocker=true)
class ProjectMemberReportIntegrationTest extends MessengerApiTestSupport {
	@Container @ServiceConnection static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
	Member host,reporter,target,admin;long project;
	@BeforeEach void fixture(){jdbc.update("DELETE FROM reports");host=member("host");reporter=member("reporter");target=member("target");admin=member("admin");jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?",admin.getId());var data=new ProjectChatTestData(jdbc);project=data.project(host.getId(),"팀원 신고",LocalDate.of(2026,11,1));data.approve(project,reporter.getId());data.approve(project,target.getId());}
	String path(){return "/api/v1/projects/"+project+"/members/"+mid(target)+"/reports";}
	long mid(Member u){return jdbc.queryForObject("SELECT id FROM project_members WHERE project_id=? AND user_id=?",Long.class,project,u.getId());}
	long report() throws Exception {return json(postAs(reporter,path(),Map.of("reason","기타","detail","검토 요청")).andExpect(status().isCreated())).get("id").asLong();}
	org.springframework.test.web.servlet.ResultActions handle(long id,Object body) throws Exception{return mvc.perform(patch("/api/v1/admin/project-reports/"+id).header("Authorization","Bearer "+jwt.createAccessToken(admin.getEmail(),"ADMIN")).contentType("application/json").content(mapper.writeValueAsString(body)));}
	org.springframework.test.web.servlet.ResultActions list(String query) throws Exception{return mvc.perform(get("/api/v1/admin/project-reports"+query).header("Authorization","Bearer "+jwt.createAccessToken(admin.getEmail(),"ADMIN")));}
	@Test void storesProjectAndTargetAndKeepsGlobalReportIndependent() throws Exception {
		postAs(reporter,"/api/v1/users/"+target.getTag()+"/reports",Map.of("reason","기타")).andExpect(status().isCreated());long id=report();
		assertEquals(project,jdbc.queryForObject("SELECT project_id FROM reports WHERE id=?",Long.class,id));assertEquals(target.getId(),jdbc.queryForObject("SELECT reported_user_id FROM reports WHERE id=?",Long.class,id));
		assertEquals(reporter.getId(),jdbc.queryForObject("SELECT reporter_id FROM reports WHERE id=?",Long.class,id));assertNull(jdbc.queryForObject("SELECT post_id FROM reports WHERE id=?",Long.class,id));
		postAs(reporter,path(),Map.of("reason","기타")).andExpect(status().isConflict());postAs(reporter,"/api/v1/users/"+target.getTag()+"/reports",Map.of("reason","기타")).andExpect(status().isConflict());
	}
	@Test void validatesMembershipSelfReasonsAndHistoricalTarget() throws Exception {
		postAs(target,path(),Map.of("reason","기타")).andExpect(status().isBadRequest());postAs(member("outsider"),path(),Map.of("reason","기타")).andExpect(status().isForbidden());
		postAs(reporter,path(),Map.of("reason","unknown")).andExpect(status().isBadRequest());postAs(reporter,path(),Map.of("reason","기타","detail","x".repeat(1001))).andExpect(status().isBadRequest());
		jdbc.update("UPDATE project_members SET status='KICKED' WHERE id=?",mid(target));report();
		jdbc.update("UPDATE project_members SET status='LEFT' WHERE id=?",mid(reporter));postAs(reporter,path(),Map.of("reason","기타")).andExpect(status().isForbidden());
	}
	@Test void duplicateScopeIsPerProjectAndPublicProjectReportIsSeparate() throws Exception {
		report();postAs(reporter,"/api/v1/projects/"+project+"/reports",Map.of("reason","기타")).andExpect(status().isCreated());
		var data=new ProjectChatTestData(jdbc);project=data.project(host.getId(),"다른 프로젝트",LocalDate.of(2026,11,1));data.approve(project,reporter.getId());data.approve(project,target.getId());report();
	}
	@Test void adminFiltersAndHandlesWithoutSanctionsAndCannotReopenFinalDecision() throws Exception {
		long id=report();assertEquals(1,json(list("").andExpect(status().isOk())).get("totalElements").asInt());postAs(reporter,"/api/v1/users/"+target.getTag()+"/reports",Map.of("reason","기타")).andExpect(status().isCreated());
		var rows=json(list("?status=RECEIVED").andExpect(status().isOk()));assertEquals(1,rows.get("totalElements").asInt());assertEquals("MEMBER",rows.get("items").get(0).get("type").asText());
		handle(id,Map.of("status","RECEIVED","resolution","되돌림")).andExpect(status().isBadRequest());handle(id,Map.of("status","RESOLVED","resolution"," ")).andExpect(status().isBadRequest());
		handle(id,Map.of("status","REVIEWING","resolution","검토 중")).andExpect(status().isOk());
		handle(id,Map.of("status","RESOLVED","resolution","안내 완료","handledBy",999)).andExpect(status().isOk()).andExpect(jsonPath("$.handledBy").value(admin.getId()));
		assertNotNull(jdbc.queryForObject("SELECT handled_at FROM reports WHERE id=?",java.time.LocalDateTime.class,id));handle(id,Map.of("status","REJECTED","resolution","다시 처리")).andExpect(status().isConflict());
		assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM users WHERE id=?",String.class,target.getId()));assertEquals("ACTIVE",jdbc.queryForObject("SELECT status FROM project_members WHERE id=?",String.class,mid(target)));
		assertEquals(0,json(list("?status=RECEIVED").andExpect(status().isOk())).get("totalElements").asInt());
	}
	@Test void deniesUnauthorizedAdminAndWrongTargets() throws Exception {
		long id=report();mvc.perform(get("/api/v1/admin/project-reports").header("Authorization","Bearer "+jwt.createAccessToken(reporter.getEmail(),"ADMIN"))).andExpect(status().isForbidden());getAs(reporter,"/api/v1/admin/project-reports").andExpect(status().isForbidden());mvc.perform(get("/api/v1/admin/project-reports")).andExpect(status().isUnauthorized());
		var outsider=member("outsider");var data=new ProjectChatTestData(jdbc);long other=data.project(outsider.getId(),"외부 프로젝트",LocalDate.of(2026,11,1));long otherMember=jdbc.queryForObject("SELECT id FROM project_members WHERE project_id=?",Long.class,other);
		postAs(reporter,"/api/v1/projects/"+project+"/members/"+otherMember+"/reports",Map.of("reason","기타")).andExpect(status().isNotFound());
		postAs(reporter,"/api/v1/users/"+target.getTag()+"/reports",Map.of("reason","기타")).andExpect(status().isCreated());long global=jdbc.queryForObject("SELECT id FROM reports WHERE project_id IS NULL AND reporter_id=?",Long.class,reporter.getId());handle(global,Map.of("status","REJECTED","resolution","범위 밖")).andExpect(status().isNotFound());
	}
	@Test void concurrentReportsAndDecisionsAreSerialized() throws Exception {
		Callable<Integer> submit=()->postAs(reporter,path(),Map.of("reason","기타")).andReturn().getResponse().getStatus();assertEquals(List.of(201,409),runTogether(List.of(submit,submit)).stream().sorted().toList());
		long id=jdbc.queryForObject("SELECT id FROM reports WHERE project_id=?",Long.class,project);
		Callable<Integer> decide=()->handle(id,Map.of("status","REJECTED","resolution","중복 아님")).andReturn().getResponse().getStatus();assertEquals(List.of(200,409),runTogether(List.of(decide,decide)).stream().sorted().toList());
	}
}

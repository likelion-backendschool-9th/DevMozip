package com.devmozip.backend.controller;

import java.time.*;
import java.util.List;
import com.devmozip.backend.entity.*;
import com.devmozip.backend.entity.enums.ProjectStatus;
import com.devmozip.backend.repository.*;
import com.devmozip.backend.security.jwt.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker=true)
class ProfileReviewsIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore tokens;
	@Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc; @Autowired JwtProvider jwt;
	@Autowired ProjectRepository projects; @Autowired ProjectMemberRepository members;
	@Autowired ProjectReviewRepository reviews; @Autowired ReviewKeywordRepository keywords;
	@Autowired ReviewKeywordSelectionRepository selections;
	long publicProject;
	@DynamicPropertySource static void properties(DynamicPropertyRegistry r){
		r.add("spring.jpa.hibernate.ddl-auto",()->"create");r.add("spring.sql.init.mode",()->"never");
	}
	@BeforeEach void fixtures(){
		assertNotEquals(5442,postgres.getMappedPort(5432));
		jdbc.update("DELETE FROM review_keyword_selections");jdbc.update("DELETE FROM reviews");jdbc.update("DELETE FROM project_members");
		jdbc.update("DELETE FROM projects");jdbc.update("DELETE FROM review_keywords");jdbc.update("DELETE FROM users");
		for(int i=1;i<=3;i++)jdbc.update("INSERT INTO users(id,email,nickname,tag,role,status) VALUES (?,?,?,?,?,?)",i,"review"+i+"@test.local","동료"+i,"review-"+i,"USER",i==3?"SUSPENDED":"ACTIVE");
		publicProject=fixture(true,5,true);fixture(false,3,false);
	}
	long fixture(boolean visible,int stars,boolean pending){
		var now=LocalDateTime.of(2026,11,10,10,0);var date=now.toLocalDate();
		var p=new Project(2,visible?"공개 프로젝트":"비공개 제목","요약","설명",null,"https://example.test/image.png",visible,List.of(),List.of(),List.of(),date,date,date,date,now);
		p.changeStatus(ProjectStatus.COMPLETED,now);p=projects.saveAndFlush(p);
		var author=members.saveAndFlush(ProjectMember.host(p,2,now));
		var target=members.saveAndFlush(ProjectMember.participant(p,1,"백엔드",now));
		var r=new ProjectReview(p,author,target,"DO_NOT_EXPOSE_TOKEN",now);r.submit(stars,now);r=reviews.saveAndFlush(r);
		if(visible){var k=keywords.saveAndFlush(new ReviewKeyword("소통",now));selections.saveAndFlush(new ReviewKeywordSelection(r,k,now));jdbc.update("UPDATE review_keywords SET is_active=false WHERE id=?",k.getId());}
		if(pending)reviews.saveAndFlush(new ProjectReview(p,target,author,"PENDING_TOKEN",now));
		return p.getId();
	}
	String token(int id){return "Bearer "+jwt.createAccessToken("review"+id+"@test.local","USER");}
	@Test void ownSummaryAndIdentity()throws Exception{
		var body=mvc.perform(get("/api/v1/users/me/reviews").header("Authorization",token(1)).param("userId","2").param("size","1"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(1)).andExpect(jsonPath("$.count").value(2))
			.andExpect(jsonPath("$.average").value(4.0)).andExpect(jsonPath("$.distribution[2]").value(1)).andExpect(jsonPath("$.distribution[4]").value(1))
			.andExpect(jsonPath("$.keywords[0].name").value("소통")).andExpect(jsonPath("$.items.length()").value(1))
			.andExpect(jsonPath("$.items[0].projectTitle").value("비공개 제목")).andExpect(jsonPath("$.totalPages").value(2)).andReturn().getResponse().getContentAsString();
		assertFalse(body.contains("TOKEN"));assertFalse(body.contains("tokenHash"));assertFalse(body.contains("email"));
		assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM reviews",Integer.class));
	}
	@Test void publicReviewHidesPrivateAndDeletedProjectNames()throws Exception{
		mvc.perform(get("/api/v1/users/review-1/reviews")).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2))
			.andExpect(jsonPath("$.items[0].projectTitle").value("비공개 프로젝트")).andExpect(jsonPath("$.items[0].projectId").isEmpty())
			.andExpect(jsonPath("$.items[1].projectTitle").value("공개 프로젝트")).andExpect(jsonPath("$.items[1].projectId").value(publicProject))
			.andExpect(jsonPath("$.items[1].authorNickname").value("동료2")).andExpect(jsonPath("$.items[1].keywords[0]").value("소통"))
			.andExpect(jsonPath("$.items[1].submittedAt").value("2026-11-10T10:00:00+09:00"));
		jdbc.update("UPDATE projects SET deleted_at=localtimestamp WHERE id=?",publicProject);
		mvc.perform(get("/api/v1/users/review-1/reviews")).andExpect(status().isOk()).andExpect(jsonPath("$.items[1].projectId").isEmpty());
	}
	@Test void accessAndEmptySummary()throws Exception{
		mvc.perform(get("/api/v1/users/me/reviews")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/users/review-3/reviews")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/users/missing/reviews")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/users/me/reviews").header("Authorization",token(2))).andExpect(status().isOk())
			.andExpect(jsonPath("$.count").value(0)).andExpect(jsonPath("$.average").value(0)).andExpect(jsonPath("$.items.length()").value(0));
	}
	@Test void invalidPageAndStoreFailure()throws Exception{
		mvc.perform(get("/api/v1/users/review-1/reviews").param("page","wrong")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/users/review-1/reviews").param("size","101")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/users/me/reviews").header("Authorization",token(1)).param("page","-1")).andExpect(status().isBadRequest());
		jdbc.execute("ALTER TABLE reviews RENAME TO reviews_unavailable");
		try{mvc.perform(get("/api/v1/users/review-1/reviews")).andExpect(status().isServiceUnavailable());}
		finally{jdbc.execute("ALTER TABLE reviews_unavailable RENAME TO reviews");}
	}
}

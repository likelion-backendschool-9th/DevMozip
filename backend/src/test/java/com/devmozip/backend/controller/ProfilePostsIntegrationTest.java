package com.devmozip.backend.controller;

import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker=true)
class ProfilePostsIntegrationTest {
	@Container @ServiceConnection static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
	@MockitoBean RefreshTokenStore tokens;
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired JwtProvider jwt;
	@DynamicPropertySource static void properties(DynamicPropertyRegistry r){
		r.add("spring.jpa.hibernate.ddl-auto",()->"create");r.add("spring.sql.init.mode",()->"never");
	}
	@BeforeEach void fixtures(){
		assertNotEquals(5442,postgres.getMappedPort(5432));
		// posts는 Post 엔티티가 만든 테이블(ddl-auto: create)을 그대로 쓴다. 운영 조회 SQL을 실제 열 구성으로 확인한다
		jdbc.update("DELETE FROM posts");jdbc.update("DELETE FROM users");
		for(int i=1;i<=4;i++)jdbc.update("INSERT INTO users(id,email,nickname,tag,role,status) VALUES (?,?,?,?,?,?)",i,"posts"+i+"@test.local","작성자"+i,"posts-"+i,"USER",i==3?"SUSPENDED":i==4?"WARNED":"ACTIVE");
		post(1,1,null,"POST","VISIBLE",null);
		post(2,2,null,"POST","VISIBLE",null);
		post(3,1,2L,"REPOST","VISIBLE",null);
		post(4,1,null,"POST","HIDDEN",null);
		post(5,1,null,"POST","VISIBLE","2026-11-10 12:00:00");
		post(6,3,null,"POST","VISIBLE",null);
		post(7,1,6L,"REPOST","VISIBLE",null);
		post(8,4,null,"POST","VISIBLE",null);
	}
	void post(long id,long author,Long original,String type,String visibility,String deleted){
		jdbc.update("INSERT INTO posts(id,author_id,original_post_id,type,category,title,content,tech_stacks,interests,image_urls,view_count,visibility,created_at,updated_at,deleted_at) "
			+"VALUES (?,?,?,?,?,?,?,CAST('[]' AS jsonb),CAST('[]' AS jsonb),CAST('[]' AS jsonb),0,?,TIMESTAMP '2026-11-10 10:00:00',TIMESTAMP '2026-11-10 10:00:00',CAST(? AS TIMESTAMP))",
			id,author,original,type,"INFO","제목"+id,"x".repeat(400),visibility,deleted);
	}
	String token(int id){return "Bearer "+jwt.createAccessToken("posts"+id+"@test.local","USER");}
	@Test void ownIdentityAndStablePages()throws Exception{
		mvc.perform(get("/api/v1/users/me/posts").header("Authorization",token(1)).param("userId","2").param("size","1"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
			.andExpect(jsonPath("$.items[0].id").value(3)).andExpect(jsonPath("$.items[0].author.id").value(1))
			.andExpect(jsonPath("$.items[0].originalPost.id").value(2)).andExpect(jsonPath("$.items[0].author.email").doesNotExist());
		mvc.perform(get("/api/v1/users/me/posts").header("Authorization",token(1)).param("page","1").param("size","1"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(1));
		assertEquals(8,jdbc.queryForObject("SELECT count(*) FROM posts",Integer.class));
	}
	@Test void publicListAndFiltering()throws Exception{
		mvc.perform(get("/api/v1/users/posts-1/posts")).andExpect(status().isOk())
			.andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].title").value("제목3"))
			.andExpect(jsonPath("$.items[0].contentPreview").value("x".repeat(300)))
			.andExpect(jsonPath("$.items[0].createdAt").value("2026-11-10T10:00:00+09:00"));
	}
	@Test void authenticationAndMissing()throws Exception{
		mvc.perform(get("/api/v1/users/me/posts")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/users/posts-3/posts")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/users/missing/posts")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/users/posts-4/posts")).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/users/me/posts").header("Authorization",token(4))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
	}
	@Test void invalidAndEmptyPages()throws Exception{
		for(String value:new String[]{"-1","x","1000001"})mvc.perform(get("/api/v1/users/posts-1/posts").param("page",value)).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/users/posts-1/posts").param("size","101")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/users/posts-2/posts").param("page","9")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
	}
	@Test void missingStoreIsNotEmpty()throws Exception{
		jdbc.execute("ALTER TABLE posts RENAME TO posts_unavailable");
		try{mvc.perform(get("/api/v1/users/posts-1/posts")).andExpect(status().isServiceUnavailable());}
		finally{jdbc.execute("ALTER TABLE posts_unavailable RENAME TO posts");}
	}
}

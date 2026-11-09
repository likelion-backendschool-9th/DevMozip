package com.devmozip.backend.controller;

import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.security.jwt.RefreshTokenStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 스키마·샘플 생성은 격리된 테스트 컨테이너에서만 수행한다. 공유 DB는 변경하지 않는다. */
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker=true)
class PortfolioQueryIntegrationTest {
    @Container @ServiceConnection static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17");
    @MockitoBean RefreshTokenStore refreshTokens;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProvider jwt;
    @Autowired ObjectMapper mapper;
    static final String OWN="/api/v1/users/me/portfolios", PUBLIC="/api/v1/users/portfolio-user-1/portfolios", DETAIL="/api/v1/portfolios/";
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        r.add("spring.jpa.hibernate.ddl-auto",()->"create");r.add("spring.sql.init.mode",()->"never");
    }
    @BeforeEach void fixtures(){
        assertNotEquals(5442,postgres.getMappedPort(5432));
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS portfolios(id BIGINT PRIMARY KEY,user_id BIGINT REFERENCES users(id),
              title VARCHAR(255),thumbnail_url VARCHAR(2048),topic VARCHAR(255),summary TEXT,description TEXT,
              role VARCHAR(100),tech_stack JSONB,attachments JSONB,visibility VARCHAR(20),created_at TIMESTAMP,updated_at TIMESTAMP)
            """);
        jdbc.update("DELETE FROM portfolios");jdbc.update("DELETE FROM users");
        for(int id=1;id<=8;id++)jdbc.update("INSERT INTO users(id,email,nickname,tag,role,status) VALUES(?,?,?,?,?,?)",id,
                "portfolio"+id+"@test.local","개발자"+id,id==5?null:"portfolio-user-"+id,id==3?"PREMIUM":id==4?"ADMIN":"USER",id==5?"WARNED":id==6||id==7?"SUSPENDED":"ACTIVE");
        portfolio(1,1,"PUBLIC");portfolio(2,1,"PRIVATE");portfolio(3,1,"UNKNOWN");
        portfolio(4,5,"PUBLIC");portfolio(5,7,"PUBLIC");portfolio(6,1,"PUBLIC");portfolio(7,1,"PUBLIC");
        jdbc.update("UPDATE portfolios SET title=NULL,thumbnail_url=NULL,topic=NULL,summary=NULL,description=NULL,role=NULL,tech_stack=NULL,attachments=NULL,created_at=NULL,updated_at=NULL WHERE id=6");
        jdbc.update("UPDATE portfolios SET created_at='2026-11-02 10:00:00' WHERE id IN (1,2)");
    }
    void portfolio(int id,int user,String visibility){jdbc.update("""
        INSERT INTO portfolios VALUES(?,?,?,'https://example.com/cover.png','AI','공개 요약','TOP_SECRET 상세 설명',
          '백엔드','["Java","Spring Boot"]'::jsonb,'[{"name":"설계","url":"https://example.com/design.pdf","secret":"hidden"}]'::jsonb,?,
          '2026-11-01 10:00:00','2026-11-02 10:00:00')
        """,id,user,"프로젝트"+id,visibility);}
    String token(int id){return token(id,"USER");}
    String token(int id,String role){return "Bearer "+jwt.createAccessToken("portfolio"+id+"@test.local",role);}
    @Test void publicSummaryDoesNotExposePrivateOrDetail()throws Exception{
        var body=mvc.perform(get(PUBLIC)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var value=mapper.readTree(body);assertEquals(3,value.path("totalElements").asInt());
        assertEquals(1,value.path("items").get(0).path("id").asInt());
        assertEquals(7,value.path("items").get(1).path("id").asInt());
        assertEquals(6,value.path("items").get(2).path("id").asInt());
        assertFalse(body.contains("TOP_SECRET"));assertFalse(body.contains("attachments"));assertFalse(body.contains("PRIVATE"));assertFalse(body.contains("email"));
    }
    @Test void ownListUsesAuthenticatedEmailAndIncludesPrivate()throws Exception{
        mvc.perform(get(OWN).param("userId","2").header("Authorization",token(1))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4)).andExpect(jsonPath("$.items[0].id").value(2));
        mvc.perform(get(OWN).header("Authorization",token(2))).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(OWN).header("Authorization",token(5))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].userId").value(5));
    }
    @Test void protectedEndpointsRequireAuthentication()throws Exception{
        mvc.perform(get(OWN)).andExpect(status().isUnauthorized());mvc.perform(get(DETAIL+1)).andExpect(status().isUnauthorized());
        mvc.perform(get(OWN).header("Authorization",token(1)+"invalid")).andExpect(status().isUnauthorized());
    }
    @Test void detailChecksLiveDatabaseRoleAndOwnership()throws Exception{
        mvc.perform(get(DETAIL+1).header("Authorization",token(2,"PREMIUM"))).andExpect(status().isForbidden());
        mvc.perform(get(DETAIL+1).header("Authorization",token(3))).andExpect(status().isOk()).andExpect(jsonPath("$.description").value("TOP_SECRET 상세 설명"));
        mvc.perform(get(DETAIL+1).header("Authorization",token(4))).andExpect(status().isOk());
        mvc.perform(get(DETAIL+2).header("Authorization",token(1))).andExpect(status().isOk());
        for(int user:new int[]{2,3,4})mvc.perform(get(DETAIL+2).header("Authorization",token(user))).andExpect(status().isNotFound());
    }
    @Test void unavailableOwnerAndUnknownVisibilityStayHidden()throws Exception{
        for(int id:new int[]{3,5,999})mvc.perform(get(DETAIL+id).header("Authorization",token(3))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/portfolio-user-7/portfolios")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/unknown/portfolios")).andExpect(status().isNotFound());
        mvc.perform(get(OWN).header("Authorization",token(6))).andExpect(status().isForbidden());
        mvc.perform(get(OWN).header("Authorization",token(99))).andExpect(status().isNotFound());
    }
    @Test void stablePagesAndEmptyResult()throws Exception{
        mvc.perform(get(PUBLIC).param("size","1").param("page","1")).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(7)).andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get(PUBLIC).param("page","99")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(3));
        mvc.perform(get("/api/v1/users/portfolio-user-8/portfolios")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void nullsAndAttachmentsAreProjectedWithoutMutation()throws Exception{
        mvc.perform(get(DETAIL+6).header("Authorization",token(1))).andExpect(status().isOk()).andExpect(jsonPath("$.portfolio.techStack").isEmpty()).andExpect(jsonPath("$.attachments").isEmpty());
        var body=mvc.perform(get(DETAIL+1).header("Authorization",token(1))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("hidden"));assertFalse(body.contains("secret\""));assertFalse(body.contains("email"));assertFalse(body.contains("password"));
        assertEquals(7,jdbc.queryForObject("SELECT count(*) FROM portfolios",Integer.class));
        assertTrue(jdbc.queryForObject("SELECT attachments::text FROM portfolios WHERE id=1",String.class).contains("hidden"));
    }
    @Test void missingTableIs503NotEmpty()throws Exception{
        jdbc.execute("ALTER TABLE portfolios RENAME TO portfolios_unavailable");
        try{
            var body=mvc.perform(get(PUBLIC)).andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
            assertFalse(body.contains("SELECT"));assertFalse(body.contains("portfolios_unavailable"));
            mvc.perform(get(DETAIL+1).header("Authorization",token(1))).andExpect(status().isServiceUnavailable());
        }finally{jdbc.execute("ALTER TABLE portfolios_unavailable RENAME TO portfolios");}
    }
    @Test void malformedJsonShapeIs503()throws Exception{
        jdbc.update("UPDATE portfolios SET tech_stack='{}'::jsonb WHERE id=1");
        mvc.perform(get(PUBLIC)).andExpect(status().isServiceUnavailable());
        jdbc.update("UPDATE portfolios SET tech_stack='[]'::jsonb,attachments='{}'::jsonb WHERE id=1");
        mvc.perform(get(DETAIL+1).header("Authorization",token(1))).andExpect(status().isServiceUnavailable());
    }
    @ParameterizedTest @ValueSource(strings={"page=-1","page=1000001","size=0","size=101","page=oops"})
    void invalidPagination(String query)throws Exception{mvc.perform(get(PUBLIC+"?"+query)).andExpect(status().isBadRequest());}
    @ParameterizedTest @ValueSource(strings={"0","-1","oops","999999999999999999999999"})
    void invalidId(String id)throws Exception{mvc.perform(get(DETAIL+id).header("Authorization",token(1))).andExpect(status().isBadRequest());}
}

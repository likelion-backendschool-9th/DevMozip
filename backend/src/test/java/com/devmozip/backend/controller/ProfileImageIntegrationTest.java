package com.devmozip.backend.controller;

import com.devmozip.backend.security.jwt.JwtProvider;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.storage.ProfileImageTestFiles;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Sql(statements = {
        "DELETE FROM users",
        "INSERT INTO users (id, job_categories, email, nickname, tag, status, role, trust_score, banner_image, contact_hours) VALUES (1,'[\"백엔드 개발자\"]' FORMAT JSON,'owner@image.test','이미지회원','image-owner','ACTIVE','USER',36.50,'https://external.test/keep.png','평일 저녁')",
        "INSERT INTO users (id, job_categories, email, nickname, tag, status, role, trust_score) VALUES (2,'[\"백엔드 개발자\"]' FORMAT JSON,'other@image.test','다른회원','image-other','ACTIVE','USER',36.50)",
        "INSERT INTO users (id, job_categories, email, nickname, tag, status, role, trust_score) VALUES (3,'[\"백엔드 개발자\"]' FORMAT JSON,'suspended@image.test','정지회원','image-suspended','SUSPENDED','USER',36.50)"
})
class ProfileImageIntegrationTest {
    @TempDir static Path directory;
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwt;
    @Autowired ObjectMapper mapper;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String random = UUID.randomUUID().toString();
        registry.add("jwt.secret", () -> random + random);
        registry.add("auth.bootstrap.user-password", () -> random);
        registry.add("auth.bootstrap.admin-password", () -> random);
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:profile-images;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("management.health.redis.enabled", () -> "false");
        registry.add("server.address", () -> "127.0.0.1");
        // 게시글 이미지와 같은 IMAGE_STORAGE 폴더를 쓴다. nginx 없이 실행할 때는 PublicImageController가 /images/로 제공한다
        registry.add("IMAGE_STORAGE", () -> directory.resolve("images").toString());
    }

    @BeforeEach void cleanFiles() throws Exception {
        Path root = root(); Files.createDirectories(root);
        try (var files = Files.list(root)) { for (Path file : files.toList()) Files.delete(file); }
    }

    @Test void savesOnlyOwnersProfileImageAndServesActualBytes() throws Exception {
        String url = upload("profile");
        assertTrue(url.matches("/images/profile_1_[0-9]{14}\\.png"), url);
        assertTrue(Files.exists(file(url)));
        var owner = members.findById(1L).orElseThrow();
        assertEquals(url, owner.getProfileImage());
        assertEquals("https://external.test/keep.png", owner.getBannerImage());
        assertEquals("평일 저녁", owner.getContactHours());
        assertEquals("USER", owner.getRole().name());
        assertNull(members.findById(2L).orElseThrow().getProfileImage());
        mvc.perform(get(path(url))).andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(ProfileImageTestFiles.png()));
        mvc.perform(get("/api/v1/users/image-owner")).andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImage").value(url));
    }

    @Test void replacementRemovesPreviousFileAfterDatabaseCommit() throws Exception {
        String first = upload("profile"), second = upload("profile");
        assertNotEquals(first, second);
        assertFalse(Files.exists(file(first)));
        assertTrue(Files.exists(file(second)));
        assertEquals(1, countFiles());
        mvc.perform(get(path(first))).andExpect(status().isNotFound());
    }

    @Test void profileAndBannerAreStoredIndependently() throws Exception {
        String profile = upload("profile"), banner = upload("banner");
        assertTrue(banner.matches("/images/banner_1_[0-9]{14}\\.png"), banner);
        var owner = members.findById(1L).orElseThrow();
        assertEquals(profile, owner.getProfileImage()); assertEquals(banner, owner.getBannerImage());
        mvc.perform(delete("/api/v1/users/me/images/banner").header("Authorization", token("owner@image.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bannerImage").value(org.hamcrest.Matchers.nullValue()));
        assertFalse(Files.exists(file(banner)));
        assertTrue(Files.exists(file(profile)));
    }

    @Test void deletionCanBeRepeatedAndDoesNotDeleteExternalFiles() throws Exception {
        String profile = upload("profile");
        for (int index = 0; index < 2; index++) {
            mvc.perform(delete("/api/v1/users/me/images/profile").header("Authorization", token("owner@image.test")))
                    .andExpect(status().isOk());
        }
        assertFalse(Files.exists(file(profile)));
        assertNull(members.findById(1L).orElseThrow().getProfileImage());
        assertEquals("https://external.test/keep.png", members.findById(1L).orElseThrow().getBannerImage());
    }

    @Test void rollbackRemovesNewFileAndRetainsPreviousFile() throws Exception {
        String previous = upload("profile");
        jdbc.execute("ALTER TABLE users ADD CONSTRAINT profile_image_guard CHECK (id <> 1 OR profile_image = '" + previous + "')");
        try {
            requestUpload("profile", token("owner@image.test"), ProfileImageTestFiles.png(), "image/png").andExpect(status().isConflict());
            assertEquals(previous, members.findById(1L).orElseThrow().getProfileImage());
            assertTrue(Files.exists(file(previous))); assertEquals(1, countFiles());
        } finally { jdbc.execute("ALTER TABLE users DROP CONSTRAINT profile_image_guard"); }
    }

    @Test void failedDeleteRetainsDatabaseReferenceAndFile() throws Exception {
        String previous = upload("profile");
        jdbc.execute("ALTER TABLE users ADD CONSTRAINT profile_image_delete_guard CHECK (id <> 1 OR profile_image IS NOT NULL)");
        try {
            mvc.perform(delete("/api/v1/users/me/images/profile").header("Authorization", token("owner@image.test")))
                    .andExpect(status().isConflict());
            assertEquals(previous, members.findById(1L).orElseThrow().getProfileImage());
            assertTrue(Files.exists(file(previous)));
        } finally { jdbc.execute("ALTER TABLE users DROP CONSTRAINT profile_image_delete_guard"); }
    }

    @Test void sharedReferenceIsNotDeletedUntilLastReferenceIsRemoved() throws Exception {
        String previous = upload("profile");
        jdbc.update("UPDATE users SET profile_image = ? WHERE id = 2", previous);
        upload("profile");
        assertTrue(Files.exists(file(previous)));
        mvc.perform(delete("/api/v1/users/me/images/profile").header("Authorization", token("other@image.test")))
                .andExpect(status().isOk());
        assertFalse(Files.exists(file(previous)));
    }

    @Test void anonymousAndInvalidTokensCannotUpload() throws Exception {
        requestUpload("profile", "", ProfileImageTestFiles.png(), "image/png").andExpect(status().isUnauthorized());
        requestUpload("profile", "Bearer invalid", ProfileImageTestFiles.png(), "image/png").andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/users/me/images/profile")).andExpect(status().isUnauthorized());
        assertEquals(0, countFiles());
    }

    @Test void unknownAndSuspendedMembersCannotUpload() throws Exception {
        requestUpload("profile", token("missing@image.test"), ProfileImageTestFiles.png(), "image/png").andExpect(status().isNotFound());
        requestUpload("profile", token("suspended@image.test"), ProfileImageTestFiles.png(), "image/png").andExpect(status().isForbidden());
        assertEquals(0, countFiles());
    }

    @Test void rejectsUnsupportedCorruptOversizedAndUnknownKindWithoutChanges() throws Exception {
        String previous = upload("profile");
        requestUpload("profile", token("owner@image.test"), new byte[256 * 1024 + 1], "image/png").andExpect(status().is(413));
        requestUpload("profile", token("owner@image.test"), "<svg/>".getBytes(), "image/svg+xml").andExpect(status().is(415));
        requestUpload("profile", token("owner@image.test"), new byte[]{1,2,3}, "image/png").andExpect(status().isBadRequest());
        requestUpload("unknown", token("owner@image.test"), ProfileImageTestFiles.png(), "image/png").andExpect(status().isBadRequest());
        assertEquals(previous, members.findById(1L).orElseThrow().getProfileImage());
        assertEquals(1, countFiles());
    }

    @Test void fileWriteFailureDoesNotChangeDatabase() throws Exception {
        Files.delete(root()); Files.writeString(root(), "blocked");
        try {
            requestUpload("profile", token("owner@image.test"), ProfileImageTestFiles.png(), "image/png").andExpect(status().isServiceUnavailable());
            assertNull(members.findById(1L).orElseThrow().getProfileImage());
        } finally { Files.delete(root()); Files.createDirectory(root()); }
    }

    @Test void imagesAreServedLikeNginxWithoutStatusCheckAndOldPathIsGone() throws Exception {
        String url = upload("profile");
        // nginx와 같이 권한 확인 없이 제공한다. 정지 회원의 이미지도 주소를 알면 보인다 (docs/profile-local-images.md)
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = 1");
        mvc.perform(get(path(url))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=86400")));
        String name = file(url).getFileName().toString();
        mvc.perform(get("/api/v1/profile-images/" + name)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/profile-images/" + name).header("Authorization", token("other@image.test"))).andExpect(status().isNotFound());
    }

    @Test void actualHttpMultipartUploadAndImageDownloadWork() throws Exception {
        var response = httpUpload(ProfileImageTestFiles.png());
        assertEquals(200, response.statusCode());
        String url = mapper.readTree(response.body()).get("profileImage").asString();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path(url))).GET().build();
        var image = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, image.statusCode());
        assertArrayEquals(ProfileImageTestFiles.png(), image.body());
    }

    @Test void actualHttpRejectsMultipartBeyondServletLimit() throws Exception {
        assertEquals(413, httpUpload(new byte[1024 * 1024 + 1]).statusCode());
        assertEquals(0, countFiles());
    }

    private HttpResponse<String> httpUpload(byte[] bytes) throws Exception {
        String boundary = "image-test-" + UUID.randomUUID();
        byte[] start = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] end = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII);
        var body = HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(start), HttpRequest.BodyPublishers.ofByteArray(bytes), HttpRequest.BodyPublishers.ofByteArray(end));
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/users/me/images/profile"))
                .header("Authorization", token("owner@image.test")).header("Content-Type", "multipart/form-data; boundary=" + boundary).PUT(body).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String upload(String kind) throws Exception {
        String body = requestUpload(kind, token("owner@image.test"), ProfileImageTestFiles.png(), "image/png")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get(kind.equals("profile") ? "profileImage" : "bannerImage").asString();
    }

    private org.springframework.test.web.servlet.ResultActions requestUpload(String kind, String token, byte[] data, String mime) throws Exception {
        return mvc.perform(multipart(HttpMethod.PUT, "/api/v1/users/me/images/" + kind)
                .file(new MockMultipartFile("file", "../../untrusted-name.png", mime, data)).header("Authorization", token));
    }

    private String token(String email) { return "Bearer " + jwt.createAccessToken(email, "USER"); }
    private Path root() { return directory.resolve("images"); }
    private String path(String url) { return URI.create(url).getPath(); }
    private Path file(String url) { return root().resolve(path(url).substring(path(url).lastIndexOf('/') + 1)); }
    private long countFiles() throws Exception { try (var files = Files.list(root())) { return files.count(); } }
}

package com.devmozip.backend.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class LocalProfileImageStorageTest {
    // 한국 시각 2026-11-11 09:30:00
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T00:30:00Z"), ZoneId.of("Asia/Seoul"));
    @TempDir Path directory;
    LocalProfileImageStorage storage;

    @BeforeEach void setUp() { storage = new LocalProfileImageStorage(directory.toString(), CLOCK); }

    @Test void savesPngWithKindMemberAndTimeAndIgnoresUntrustedFilename() throws Exception {
        byte[] bytes = ProfileImageTestFiles.png();
        var saved = storage.save("profile", 7, new MockMultipartFile("file", "../../outside.php", "image/png", bytes));
        assertEquals("profile_7_20261111093000.png", saved.filename());
        assertEquals("/images/profile_7_20261111093000.png", saved.url());
        assertArrayEquals(bytes, Files.readAllBytes(directory.resolve(saved.filename())));
        assertEquals(1, countFiles());
    }

    @Test void validatesJpegAndWebpByDecodingAndNamesBanner() throws Exception {
        var jpeg = storage.save("banner", 7, new MockMultipartFile("file", "photo.jpg", "image/jpeg", ProfileImageTestFiles.jpeg()));
        var webp = storage.save("profile", 7, new MockMultipartFile("file", "photo.webp", "image/webp", ProfileImageTestFiles.webp()));
        assertEquals("banner_7_20261111093000.jpg", jpeg.filename());
        assertEquals("profile_7_20261111093000.webp", webp.filename());
        assertArrayEquals(ProfileImageTestFiles.webp(), Files.readAllBytes(directory.resolve(webp.filename())));
        assertEquals(2, countFiles());
    }

    @Test void sameSecondUploadUsesNextSecondWithoutOverwriting() throws Exception {
        var first = storage.save("profile", 7, new MockMultipartFile("file", "a.png", "image/png", ProfileImageTestFiles.png()));
        var second = storage.save("profile", 7, new MockMultipartFile("file", "b.jpg", "image/jpeg", ProfileImageTestFiles.jpeg()));
        var third = storage.save("profile", 7, new MockMultipartFile("file", "c.png", "image/png", ProfileImageTestFiles.png()));
        assertEquals("profile_7_20261111093000.png", first.filename());
        // 확장자가 달라도 이름은 다르므로 같은 초를 쓴다
        assertEquals("profile_7_20261111093000.jpg", second.filename());
        assertEquals("profile_7_20261111093001.png", third.filename());
        assertEquals(3, countFiles());
    }

    @Test void rejectsUnknownKindAndMember() {
        var file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        assertThrows(IllegalArgumentException.class, () -> storage.save("feed", 7, file));
        assertThrows(IllegalArgumentException.class, () -> storage.save("profile", 0, file));
    }

    @Test void rejectsFalseMimeAndCorruptImages() throws Exception {
        var mismatch = new MockMultipartFile("file", "photo.jpg", "image/jpeg", ProfileImageTestFiles.png());
        assertStatus(400, () -> storage.save("profile", 1, mismatch));
        assertStatus(400, () -> storage.save("profile", 1, new MockMultipartFile("file", "bad.png", "image/png", new byte[]{1,2,3})));
        byte[] truncated = Arrays.copyOf(ProfileImageTestFiles.png(), 16);
        assertStatus(400, () -> storage.save("profile", 1, new MockMultipartFile("file", "bad.png", "image/png", truncated)));
        assertEquals(0, countFiles());
    }

    @Test void rejectsOversizedDimensionsBeforeDecodingPixels() throws Exception {
        byte[] bytes = ProfileImageTestFiles.png();
        var buffer = java.nio.ByteBuffer.wrap(bytes);
        buffer.putInt(16, 100_000); buffer.putInt(20, 100_000);
        var crc = new java.util.zip.CRC32(); crc.update(bytes, 12, 17);
        buffer.putInt(29, (int) crc.getValue());
        assertStatus(400, () -> storage.save("profile", 1, new MockMultipartFile("file", "large.png", "image/png", bytes)));
        assertEquals(0, countFiles());
    }

    @Test void rejectsEmptyOversizedAndUnsupportedFiles() throws Exception {
        assertStatus(400, () -> storage.save("profile", 1, new MockMultipartFile("file", new byte[0])));
        assertStatus(413, () -> storage.save("profile", 1, new MockMultipartFile("file", "big.png", "image/png", new byte[256 * 1024 + 1])));
        assertStatus(415, () -> storage.save("profile", 1, new MockMultipartFile("file", "a.svg", "image/svg+xml", "<svg/>".getBytes())));
        assertEquals(0, countFiles());
    }

    @Test void acceptsMaximumInputSize() throws Exception {
        byte[] padded = Arrays.copyOf(ProfileImageTestFiles.png(), 256 * 1024);
        var saved = storage.save("profile", 1, new MockMultipartFile("file", "max.png", "image/png", padded));
        assertEquals(256 * 1024, Files.size(directory.resolve(saved.filename())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.png", "../../test", "/tmp/test.png", "secret.txt",
            "00000000-0000-0000-0000-000000000000.png", "feed_1_20261111093000_1.png", "profile_1_20261111093000.svg"})
    void neverResolvesUnmanagedPaths(String filename) {
        assertStatus(404, () -> storage.delete(filename));
        assertStatus(404, () -> storage.url(filename));
    }

    @Test void refusesSymlinkFolder() throws Exception {
        Path target = Files.createDirectory(directory.resolve("target"));
        Path link = Files.createSymbolicLink(directory.resolve("link"), target);
        var linked = new LocalProfileImageStorage(link.toString(), CLOCK);
        assertStatus(503, () -> linked.save("profile", 1, new MockMultipartFile("file", "a.png", "image/png", ProfileImageTestFiles.png())));
        try (var files = Files.list(target)) { assertEquals(0, files.count()); }
    }

    @Test void cannotWriteIntoARegularFileAndDoesNotExposePath() throws Exception {
        Path blocked = directory.resolve("not-a-folder"); Files.writeString(blocked, "keep");
        var invalid = new LocalProfileImageStorage(blocked.toString(), CLOCK);
        var exception = assertThrows(ResponseStatusException.class,
                () -> invalid.save("profile", 1, new MockMultipartFile("file", "a.png", "image/png", ProfileImageTestFiles.png())));
        assertEquals(503, exception.getStatusCode().value());
        assertFalse(exception.getReason().contains(blocked.toString()));
        assertEquals("keep", Files.readString(blocked));
    }

    @Test void onlyRecognizesManagedUrls() {
        assertEquals("profile_1_20261111093000.png", storage.ownedFilename("/images/profile_1_20261111093000.png"));
        assertEquals("banner_12_20261111093000.webp", storage.ownedFilename("/images/banner_12_20261111093000.webp"));
        assertNull(storage.ownedFilename("http://external.test/a.png"));
        assertNull(storage.ownedFilename("/images/feed_1_20261111093000_1.png"));
        assertNull(storage.ownedFilename("/images/../secret"));
        assertNull(storage.ownedFilename("https://example.test/images/profile_1_20261111093000.png"));
        // 이전 형식(이관 전) 주소는 이 저장소의 파일로 보지 않는다
        assertNull(storage.ownedFilename("http://localhost:8080/api/v1/profile-images/00000000-0000-0000-0000-000000000000.png"));
    }

    private long countFiles() throws Exception { try (var files = Files.list(directory)) { return files.count(); } }
    private void assertStatus(int status, org.junit.jupiter.api.function.Executable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action).getStatusCode().value());
    }
}

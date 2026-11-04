package com.devmozip.backend.storage;

import java.nio.file.Files;
import java.nio.file.Path;
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
    @TempDir Path directory;
    LocalProfileImageStorage storage;

    @BeforeEach void setUp() { storage = new LocalProfileImageStorage(directory.toString(), "http://localhost:8080"); }

    @Test void validatesPngAndIgnoresUntrustedFilename() throws Exception {
        byte[] bytes = ProfileImageTestFiles.png();
        var saved = storage.save(new MockMultipartFile("file", "../../outside.php", "image/png", bytes));
        assertTrue(saved.filename().matches("[0-9a-f-]{36}\\.png"));
        assertArrayEquals(bytes, storage.read(saved.filename()));
        assertEquals("http://localhost:8080/api/v1/profile-images/" + saved.filename(), saved.url());
        assertEquals(1, countFiles());
    }

    @Test void validatesJpegAndWebpByDecoding() throws Exception {
        for (var file : new MockMultipartFile[]{
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", ProfileImageTestFiles.jpeg()),
                new MockMultipartFile("file", "photo.webp", "image/webp", ProfileImageTestFiles.webp())}) {
            var saved = storage.save(file);
            assertArrayEquals(file.getBytes(), storage.read(saved.filename()));
        }
        assertEquals(2, countFiles());
    }

    @Test void rejectsFalseMimeAndCorruptImages() throws Exception {
        var mismatch = new MockMultipartFile("file", "photo.jpg", "image/jpeg", ProfileImageTestFiles.png());
        assertStatus(400, () -> storage.save(mismatch));
        assertStatus(400, () -> storage.save(new MockMultipartFile("file", "bad.png", "image/png", new byte[]{1,2,3})));
        byte[] truncated = Arrays.copyOf(ProfileImageTestFiles.png(), 16);
        assertStatus(400, () -> storage.save(new MockMultipartFile("file", "bad.png", "image/png", truncated)));
        assertEquals(0, countFiles());
    }

    @Test void rejectsOversizedDimensionsBeforeDecodingPixels() throws Exception {
        byte[] bytes = ProfileImageTestFiles.png();
        var buffer = java.nio.ByteBuffer.wrap(bytes);
        buffer.putInt(16, 100_000); buffer.putInt(20, 100_000);
        var crc = new java.util.zip.CRC32(); crc.update(bytes, 12, 17);
        buffer.putInt(29, (int) crc.getValue());
        assertStatus(400, () -> storage.save(new MockMultipartFile("file", "large.png", "image/png", bytes)));
        assertEquals(0, countFiles());
    }

    @Test void rejectsEmptyOversizedAndUnsupportedFiles() throws Exception {
        assertStatus(400, () -> storage.save(new MockMultipartFile("file", new byte[0])));
        assertStatus(413, () -> storage.save(new MockMultipartFile("file", "big.png", "image/png", new byte[256 * 1024 + 1])));
        assertStatus(415, () -> storage.save(new MockMultipartFile("file", "a.svg", "image/svg+xml", "<svg/>".getBytes())));
        assertEquals(0, countFiles());
    }

    @Test void acceptsMaximumInputSize() throws Exception {
        byte[] padded = Arrays.copyOf(ProfileImageTestFiles.png(), 256 * 1024);
        var saved = storage.save(new MockMultipartFile("file", "max.png", "image/png", padded));
        assertEquals(256 * 1024, storage.read(saved.filename()).length);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside.png", "../../test", "/tmp/test.png", "secret.txt", "00000000-0000-0000-0000-000000000000.svg"})
    void neverResolvesUnmanagedPaths(String filename) {
        assertStatus(404, () -> storage.read(filename));
    }

    @Test void doesNotFollowImageSymlinks() throws Exception {
        Path outside = Files.createTempFile("profile-image-outside-", ".png");
        try {
            String name = "00000000-0000-0000-0000-000000000000.png";
            Files.createSymbolicLink(directory.resolve(name), outside);
            assertStatus(404, () -> storage.read(name));
        } finally { Files.deleteIfExists(outside); }
    }

    @Test void cannotWriteIntoARegularFileAndDoesNotExposePath() throws Exception {
        Path blocked = directory.resolve("not-a-folder"); Files.writeString(blocked, "keep");
        var invalid = new LocalProfileImageStorage(blocked.toString(), "http://localhost:8080");
        var exception = assertThrows(ResponseStatusException.class,
                () -> invalid.save(new MockMultipartFile("file", "a.png", "image/png", ProfileImageTestFiles.png())));
        assertEquals(503, exception.getStatusCode().value());
        assertFalse(exception.getReason().contains(blocked.toString()));
        assertEquals("keep", Files.readString(blocked));
    }

    @Test void onlyRecognizesManagedUrls() {
        assertNull(storage.ownedFilename("http://external.test/a.png"));
        assertNull(storage.ownedFilename("http://localhost:8080/api/v1/profile-images/../secret"));
    }

    private long countFiles() throws Exception { try (var files = Files.list(directory)) { return files.count(); } }
    private void assertStatus(int status, org.junit.jupiter.api.function.Executable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action).getStatusCode().value());
    }
}

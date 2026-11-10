package com.devmozip.backend.storage;

import java.nio.file.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class LocalPortfolioFileStorageTest {
    // 한국 시각 2026-11-11 09:30:00
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T00:30:00Z"), ZoneId.of("Asia/Seoul"));
    static final String LEGACY = "12345678-1234-1234-1234-123456789abc";
    @TempDir Path root;
    LocalPortfolioFileStorage storage;
    @BeforeEach void setup() { storage = new LocalPortfolioFileStorage(root.toString(), JsonMapper.builder().build(), CLOCK); }
    @Test void persistsOwnerNameAndBytesWithMemberAndTimeId() throws Exception {
        byte[] bytes = ProfileImageTestFiles.png();
        var saved = storage.save(5, new MockMultipartFile("file", "설계.png", "image/png", bytes));
        assertEquals("portfolio_5_20261111093000", saved.id());
        assertEquals(5, storage.metadata(saved.id()).ownerId());
        assertEquals("설계.png", saved.name()); assertEquals("/api/files/portfolio_5_20261111093000", saved.response().url());
        assertArrayEquals(bytes, Files.readAllBytes(root.resolve("portfolio_5_20261111093000.png")));
        assertTrue(Files.isRegularFile(root.resolve("portfolio_5_20261111093000.json")));
        assertArrayEquals(bytes, storage.read(saved));
        assertEquals("portfolio_5_20261111093000.png", storage.storedName(saved));
    }
    @Test void readsJpegWebpAndPdfWithRealExtensions() throws Exception {
        String[] names = {"portfolio_1_20261111093000.jpg", "portfolio_1_20261111093001.webp", "portfolio_1_20261111093002.pdf"};
        var files = new MockMultipartFile[]{new MockMultipartFile("file","a.jpeg","image/jpeg",ProfileImageTestFiles.jpeg()),
                new MockMultipartFile("file","a.webp","image/webp",ProfileImageTestFiles.webp()),
                new MockMultipartFile("file","a.pdf","application/pdf","%PDF-1.7\n%%EOF".getBytes())};
        for (int i = 0; i < files.length; i++) {
            var saved = storage.save(1, files[i]); assertArrayEquals(files[i].getBytes(), storage.read(saved));
            assertEquals(names[i], storage.storedName(saved));
        }
    }
    @Test void sameSecondUploadsOfOneMemberUseNextSecondAndOtherMembersDoNotCollide() throws Exception {
        var first = storage.save(1, new MockMultipartFile("file","a.png","image/png",ProfileImageTestFiles.png()));
        var second = storage.save(1, new MockMultipartFile("file","b.pdf","application/pdf","%PDF-1.7\n%%EOF".getBytes()));
        var other = storage.save(2, new MockMultipartFile("file","c.png","image/png",ProfileImageTestFiles.png()));
        assertEquals("portfolio_1_20261111093000", first.id());
        // 확장자가 달라도 같은 id의 메타데이터(.json)가 겹치므로 다음 초를 쓴다
        assertEquals("portfolio_1_20261111093001", second.id());
        assertEquals("portfolio_2_20261111093000", other.id());
        assertEquals("b.pdf", storage.metadata(second.id()).name());
    }
    @Test void readsMigratedLegacyUuidFiles() throws Exception {
        byte[] bytes = "%PDF-1.7\n%%EOF".getBytes();
        Files.write(root.resolve(LEGACY + ".pdf"), bytes);
        Files.writeString(root.resolve(LEGACY + ".json"), "{\"id\":\"" + LEGACY + "\",\"ownerId\":3,\"name\":\"이전.pdf\",\"contentType\":\"application/pdf\",\"size\":" + bytes.length + "}");
        var meta = storage.metadata(LEGACY);
        assertEquals(3, meta.ownerId()); assertArrayEquals(bytes, storage.read(meta));
        assertEquals(LEGACY + ".pdf", storage.storedName(meta));
    }
    @Test void rejectsMetadataWhoseOwnerDiffersFromId() throws Exception {
        Files.write(root.resolve("portfolio_5_20261111093000.png"), ProfileImageTestFiles.png());
        Files.writeString(root.resolve("portfolio_5_20261111093000.json"), "{\"id\":\"portfolio_5_20261111093000\",\"ownerId\":6,\"name\":\"a.png\",\"contentType\":\"image/png\",\"size\":10}");
        status(404, () -> storage.metadata("portfolio_5_20261111093000"));
    }
    @Test void rejectsPathNamesMimeSpoofingAndCorruption() throws Exception {
        for (String name : new String[]{"../a.png", "C:\\a.png", "a\r\n.png"}) status(400, () -> storage.save(1,new MockMultipartFile("file",name,"image/png",ProfileImageTestFiles.png())));
        status(415, () -> storage.save(1,new MockMultipartFile("file","a.html","image/png",ProfileImageTestFiles.png())));
        status(400, () -> storage.save(1,new MockMultipartFile("file","a.jpg","image/jpeg",ProfileImageTestFiles.png())));
        status(400, () -> storage.save(1,new MockMultipartFile("file","a.png","image/png",new byte[]{1,2,3})));
        status(400, () -> storage.save(1,new MockMultipartFile("file","a.pdf","application/pdf","not a pdf".getBytes())));
    }
    @Test void enforcesLimitsAndEmptyFile() {
        status(400, () -> storage.save(1,new MockMultipartFile("file","a.png","image/png",new byte[0])));
        status(413, () -> storage.save(1,new MockMultipartFile("file","a.png","image/png",new byte[LocalPortfolioFileStorage.IMAGE_MAX+1])));
        status(413, () -> storage.save(1,new MockMultipartFile("file","a.pdf","application/pdf",new byte[LocalPortfolioFileStorage.PDF_MAX+1])));
    }
    @Test void refusesTraversalAndDataSymlink() throws Exception {
        for (String id : new String[]{"../outside", "portfolio_0_20261111093000", "portfolio_1_2026111109300", "feed_1_20261111093000"})
            status(404, () -> storage.metadata(id));
        var saved=storage.save(1,new MockMultipartFile("file","a.png","image/png",ProfileImageTestFiles.png()));
        Files.delete(root.resolve(saved.fileName()));
        Path outside=Files.createTempFile(root,"outside", ".txt");
        Files.createSymbolicLink(root.resolve(saved.fileName()),outside);
        status(404, () -> storage.read(saved));
        status(404, () -> storage.storedName(saved));
    }
    @Test void missingDataFileIsNotFound() throws Exception {
        var saved=storage.save(1,new MockMultipartFile("file","a.png","image/png",ProfileImageTestFiles.png()));
        Files.delete(root.resolve(saved.fileName()));
        status(404, () -> storage.storedName(saved));
    }
    private void status(int code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(ResponseStatusException.class, action).getStatusCode().value());
    }
}

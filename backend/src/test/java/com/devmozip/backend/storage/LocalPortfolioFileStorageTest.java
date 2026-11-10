package com.devmozip.backend.storage;

import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class LocalPortfolioFileStorageTest {
    @TempDir Path root;
    LocalPortfolioFileStorage storage;
    @BeforeEach void setup() { storage = new LocalPortfolioFileStorage(root.toString(), JsonMapper.builder().build()); }
    @Test void persistsOwnerNameAndBytesWithServerId() throws Exception {
        byte[] bytes = ProfileImageTestFiles.png();
        var saved = storage.save(5, new MockMultipartFile("file", "설계.png", "image/png", bytes));
        assertEquals(5, storage.metadata(saved.id()).ownerId());
        assertEquals("설계.png", saved.name()); assertEquals("/api/files/" + saved.id(), saved.response().url());
        assertArrayEquals(bytes, storage.read(saved));
    }
    @Test void readsJpegWebpAndPdf() throws Exception {
        for (var f : new MockMultipartFile[]{new MockMultipartFile("file","a.jpeg","image/jpeg",ProfileImageTestFiles.jpeg()),
                new MockMultipartFile("file","a.webp","image/webp",ProfileImageTestFiles.webp()),
                new MockMultipartFile("file","a.pdf","application/pdf","%PDF-1.7\n%%EOF".getBytes())}) {
            var saved = storage.save(1, f); assertArrayEquals(f.getBytes(), storage.read(saved));
        }
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
        status(404, () -> storage.metadata("../outside"));
        var saved=storage.save(1,new MockMultipartFile("file","a.png","image/png",ProfileImageTestFiles.png()));
        Files.delete(root.resolve(saved.id()+".bin"));
        Path outside=Files.createTempFile(root,"outside", ".txt");
        Files.createSymbolicLink(root.resolve(saved.id()+".bin"),outside);
        status(404, () -> storage.read(saved));
    }
    private void status(int code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(ResponseStatusException.class, action).getStatusCode().value());
    }
}

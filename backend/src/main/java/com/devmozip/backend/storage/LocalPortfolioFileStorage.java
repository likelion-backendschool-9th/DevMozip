package com.devmozip.backend.storage;

import com.devmozip.backend.dto.portfolio.PortfolioFileResponse;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** 파일과 소유자 메타데이터를 서버 전용 폴더에 보존한다. 자동 삭제·공개 정적 폴더 노출은 하지 않는다. */
@Component
public class LocalPortfolioFileStorage {
    public static final int IMAGE_MAX = 256 * 1024;
    public static final int PDF_MAX = 10 * 1024 * 1024;
    public record Metadata(String id, long ownerId, String name, String contentType, long size) {
        public PortfolioFileResponse response() { return new PortfolioFileResponse(id, "/api/files/" + id, name, contentType, size); }
    }
    private final Path root;
    private final ObjectMapper mapper;
    public LocalPortfolioFileStorage(@Value("${portfolio.files.directory:${PORTFOLIO_FILE_STORAGE:./uploads/portfolio-files}}") String directory, ObjectMapper mapper) {
        root = Path.of(directory).toAbsolutePath().normalize(); this.mapper = mapper;
    }
    public Metadata save(long ownerId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw failure(400, "파일을 선택해 주세요.");
        String name = Objects.toString(file.getOriginalFilename(), "");
        if (name.isBlank() || name.length() > 255 || name.contains("/") || name.contains("\\") || name.chars().anyMatch(c -> c < 32 || c == 127))
            throw failure(400, "파일 이름을 확인해 주세요.");
        String type = Objects.toString(file.getContentType(), "").toLowerCase(Locale.ROOT);
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        boolean pdf = type.equals("application/pdf") && extension.equals("pdf");
        String expected = switch (type) { case "image/png" -> "png"; case "image/jpeg" -> "jpg"; case "image/webp" -> "webp"; default -> ""; };
        if (!pdf && (expected.isEmpty() || !(extension.equals(expected) || expected.equals("jpg") && extension.equals("jpeg"))))
            throw failure(415, "PNG·JPEG·WebP 이미지 또는 PDF만 업로드할 수 있습니다.");
        int max = pdf ? PDF_MAX : IMAGE_MAX;
        if (file.getSize() > max) throw failure(413, pdf ? "PDF는 10MB 이하여야 합니다." : "이미지는 256KB 이하여야 합니다.");
        byte[] bytes;
        try (var in = file.getInputStream()) { bytes = in.readNBytes(max + 1); }
        catch (IOException e) { throw failure(503, "파일을 읽을 수 없습니다."); }
        if (bytes.length > max) throw failure(413, "파일 크기 제한을 초과했습니다.");
        if (pdf) {
            // PDF는 브라우저에 inline 렌더하지 않고 다운로드로만 제공한다.
            if (bytes.length < 8 || !new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))
                throw failure(400, "PDF 파일 내용을 확인해 주세요.");
        } else validateImage(bytes, expected);
        String id = UUID.randomUUID().toString();
        Metadata metadata = new Metadata(id, ownerId, name, type, bytes.length);
        Path data = path(id, ".bin"), meta = path(id, ".json");
        boolean created = false;
        try {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw new IOException("symlink");
            Files.write(data, bytes, StandardOpenOption.CREATE_NEW); created = true;
            Files.writeString(meta, mapper.writeValueAsString(metadata), StandardOpenOption.CREATE_NEW);
            return metadata;
        } catch (IOException e) {
            if (created) try { Files.deleteIfExists(data); } catch (IOException ignored) { /* 운영 점검 대상 */ }
            throw failure(503, "파일 저장소를 사용할 수 없습니다.");
        }
    }
    private void validateImage(byte[] bytes, String expected) {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw failure(400, "읽을 수 없는 이미지입니다.");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String actual = reader.getFormatName().toLowerCase(Locale.ROOT).replace("jpeg", "jpg");
                if (!expected.equals(actual) || reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0
                    || (long) reader.getWidth(0) * reader.getHeight(0) > 16_000_000 || reader.read(0) == null)
                    throw failure(400, "손상되었거나 형식·해상도가 올바르지 않은 이미지입니다.");
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException e) { throw failure(400, "손상된 이미지입니다."); }
    }
    public Metadata metadata(String id) {
        Path meta = path(id, ".json");
        if (!Files.isRegularFile(meta, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) throw failure(404, "파일을 찾을 수 없습니다.");
        try (var in = Files.newInputStream(meta, LinkOption.NOFOLLOW_LINKS)) {
            byte[] raw = in.readNBytes(4097); if (raw.length > 4096) throw new IOException("metadata");
            Metadata m = mapper.readValue(raw, Metadata.class);
            if (!id.equals(m.id()) || m.ownerId() <= 0 || m.size() <= 0 || m.size() > PDF_MAX) throw new IOException("metadata");
            return m;
        } catch (IOException | RuntimeException e) { throw failure(404, "파일을 찾을 수 없습니다."); }
    }
    public byte[] read(Metadata m) {
        Path data = path(m.id(), ".bin");
        if (!Files.isRegularFile(data, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) throw failure(404, "파일을 찾을 수 없습니다.");
        try (var in = Files.newInputStream(data, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(PDF_MAX + 1);
            if (bytes.length != m.size()) throw new IOException("size");
            return bytes;
        } catch (IOException e) { throw failure(404, "파일을 찾을 수 없습니다."); }
    }
    private Path path(String id, String suffix) {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw failure(404, "파일을 찾을 수 없습니다.");
        return root.resolve(id + suffix);
    }
    public static ResponseStatusException failure(int status, String message) { return new ResponseStatusException(HttpStatus.valueOf(status), message); }
}

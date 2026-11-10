package com.devmozip.backend.storage;

import com.devmozip.backend.dto.portfolio.PortfolioFileResponse;
import java.io.*;
import java.nio.file.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * 포트폴리오 파일과 소유자 메타데이터를 FILE_STORAGE(public/files) 폴더에 보존한다 (docs/portfolio-files-api.md).
 * 파일 이름: portfolio_{회원 id}_{업로드 시각 yyyyMMddHHmmss}.{확장자}와 같은 이름의 .json(소유자·원래 이름·형식·크기).
 * 확장자를 뺀 이름이 파일 id다. 이관한 이전 파일은 UUID id를 그대로 쓴다({UUID}.{확장자}, {UUID}.json).
 * 이 폴더는 nginx의 내부 전용 경로로만 연결한다. 권한은 백엔드가 확인하고 nginx는 X-Accel-Redirect를 받은 뒤에만 파일을 보낸다.
 * 자동 삭제는 하지 않는다.
 */
@Component
public class LocalPortfolioFileStorage {
    public static final int IMAGE_MAX = 256 * 1024;
    public static final int PDF_MAX = 10 * 1024 * 1024;
    private static final Pattern ID = Pattern.compile("portfolio_([1-9][0-9]{0,18})_[0-9]{14}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    // 같은 회원이 같은 초에 다시 올리면(표지·갤러리 동시 업로드 등) 이름이 겹친다. 다음 초 이름으로 몇 번까지 다시 시도한다.
    private static final int MAX_ATTEMPTS = 10;
    public record Metadata(String id, long ownerId, String name, String contentType, long size) {
        public PortfolioFileResponse response() { return new PortfolioFileResponse(id, "/api/files/" + id, name, contentType, size); }
        /** 폴더 안의 파일 이름({id}.{확장자}). 형식은 업로드할 때 확인한 값만 들어간다. */
        public String fileName() {
            return id + "." + switch (contentType) {
                case "image/png" -> "png"; case "image/jpeg" -> "jpg"; case "image/webp" -> "webp"; case "application/pdf" -> "pdf";
                default -> throw failure(404, "파일을 찾을 수 없습니다.");
            };
        }
    }
    private final Path root;
    private final ObjectMapper mapper;
    private final Clock clock;
    public LocalPortfolioFileStorage(@Value("${FILE_STORAGE:../public/files}") String directory, ObjectMapper mapper,
            @Qualifier("uploadClock") Clock clock) {
        root = Path.of(directory).toAbsolutePath().normalize(); this.mapper = mapper; this.clock = clock;
    }
    public Metadata save(long ownerId, MultipartFile file) {
        if (ownerId <= 0) throw new IllegalArgumentException("회원 id를 확인해 주세요.");
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
        try {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw new IOException("symlink");
        } catch (IOException e) { throw unavailable(); }
        LocalDateTime time = LocalDateTime.now(clock);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String id = "portfolio_" + ownerId + "_" + time.plusSeconds(attempt).format(STAMP);
            Metadata metadata = new Metadata(id, ownerId, name, type, bytes.length);
            Path data = root.resolve(metadata.fileName()), meta = root.resolve(id + ".json");
            String json = mapper.writeValueAsString(metadata);
            if (Files.exists(meta, LinkOption.NOFOLLOW_LINKS)) continue;
            try { Files.write(data, bytes, StandardOpenOption.CREATE_NEW); }
            catch (FileAlreadyExistsException exists) { continue; }
            catch (IOException e) { deleteQuietly(data); throw unavailable(); }
            try { Files.writeString(meta, json, StandardOpenOption.CREATE_NEW); return metadata; }
            catch (FileAlreadyExistsException exists) { deleteQuietly(data); }
            catch (IOException e) { deleteQuietly(data); throw unavailable(); }
        }
        throw unavailable();
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
            // 새 형식 id에는 소유자 id가 들어 있다. 메타데이터의 소유자와 다르면 쓰지 않는다.
            var matcher = ID.matcher(id);
            if (matcher.matches() && matcher.group(1) != null && Long.parseLong(matcher.group(1)) != m.ownerId()) throw new IOException("owner");
            m.fileName();
            return m;
        } catch (IOException | RuntimeException e) { throw failure(404, "파일을 찾을 수 없습니다."); }
    }
    public byte[] read(Metadata m) {
        Path data = data(m);
        try (var in = Files.newInputStream(data, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = in.readNBytes(PDF_MAX + 1);
            if (bytes.length != m.size()) throw new IOException("size");
            return bytes;
        } catch (IOException e) { throw failure(404, "파일을 찾을 수 없습니다."); }
    }
    /** nginx 내부 전용 경로로 넘길 폴더 안의 파일 이름. 파일이 없거나 심볼릭 링크이면 404다. */
    public String storedName(Metadata m) {
        data(m);
        return m.fileName();
    }
    private Path data(Metadata m) {
        Path data = path(m.id(), m.fileName().substring(m.id().length()));
        if (!Files.isRegularFile(data, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) throw failure(404, "파일을 찾을 수 없습니다.");
        return data;
    }
    private Path path(String id, String suffix) {
        if (id == null || !ID.matcher(id).matches()) throw failure(404, "파일을 찾을 수 없습니다.");
        return root.resolve(id + suffix);
    }
    private static void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { /* 운영 점검 대상 */ }
    }
    private static ResponseStatusException unavailable() { return failure(503, "파일 저장소를 사용할 수 없습니다."); }
    public static ResponseStatusException failure(int status, String message) { return new ResponseStatusException(HttpStatus.valueOf(status), message); }
}

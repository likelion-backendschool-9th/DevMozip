package com.devmozip.backend.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 프로필·배너 이미지 저장·삭제 (docs/profile-local-images.md "저장 위치 변경").
 * 게시글 이미지와 같은 IMAGE_STORAGE(public/images) 폴더를 쓰고 파일 이름 접두어로 구분한다.
 * 파일 이름: {profile|banner}_{회원 id}_{업로드 시각 yyyyMMddHHmmss}.{파일 내용으로 확인한 확장자}
 * DB에는 /images/{파일 이름}을 저장하고, 파일은 nginx(없으면 백엔드 PublicImageController)가 권한 확인 없이 제공한다.
 */
@Component
public class LocalProfileImageStorage {
    public static final int MAX_BYTES = 256 * 1024;
    public static final String URL_PREFIX = "/images/";
    private static final long MAX_PIXELS = 16_000_000;
    private static final Set<String> KINDS = Set.of("profile", "banner");
    private static final Pattern FILE_NAME = Pattern.compile("(?:profile|banner)_[1-9][0-9]{0,18}_[0-9]{14}\\.(?:png|jpg|webp)");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    // 같은 회원이 같은 초에 다시 올리면 이름이 겹친다. 다음 초 이름으로 몇 번까지 다시 시도한다.
    private static final int MAX_ATTEMPTS = 10;
    private final Path root;
    private final Clock clock;

    public LocalProfileImageStorage(@Value("${IMAGE_STORAGE:../public/images}") String directory,
            @Qualifier("uploadClock") Clock clock) {
        root = Path.of(directory).toAbsolutePath().normalize();
        this.clock = clock;
    }

    /** @param kind profile 또는 banner */
    public StoredImage save(String kind, long memberId, MultipartFile file) {
        if (!KINDS.contains(kind) || memberId <= 0) throw new IllegalArgumentException("이미지 종류와 회원 id를 확인해 주세요.");
        if (file == null || file.isEmpty()) throw badRequest("이미지 파일을 선택해 주세요.");
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException exception) { throw unavailable(); }
        if (bytes.length > MAX_BYTES) throw tooLarge();
        String extension = validateImage(bytes, file.getContentType());
        try {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw unavailable();
        } catch (IOException exception) { throw unavailable(); }
        LocalDateTime time = LocalDateTime.now(clock);
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            // 원본 파일명은 저장 경로와 확장자 판단에 사용하지 않는다.
            String name = kind + "_" + memberId + "_" + time.plusSeconds(attempt).format(STAMP) + "." + extension;
            Path target = root.resolve(name);
            OutputStream output = createNew(target);
            if (output == null) continue;
            try (output) {
                output.write(bytes);
            } catch (IOException exception) {
                try { Files.deleteIfExists(target); } catch (IOException ignored) { /* 실패 파일 정리는 후속 점검 대상 */ }
                throw unavailable();
            }
            return new StoredImage(name, URL_PREFIX + name);
        }
        throw unavailable();
    }

    /** 새 파일을 연다. 같은 이름이 이미 있으면 덮어쓰지 않고 null을 돌려준다. */
    private static OutputStream createNew(Path target) {
        try {
            return Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (FileAlreadyExistsException exists) {
            return null;
        } catch (IOException exception) { throw unavailable(); }
    }

    private String validateImage(byte[] bytes, String declaredType) {
        String expected = switch (declaredType == null ? "" : declaredType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            default -> throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PNG·JPEG·WebP 이미지만 업로드할 수 있습니다.");
        };
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw badRequest("읽을 수 없는 이미지입니다.");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String actual = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (actual.equals("jpeg")) actual = "jpg";
                if (!expected.equals(actual)) throw badRequest("파일 형식과 이미지 내용이 일치하지 않습니다.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw badRequest("이미지는 1,600만 화소 이하여야 합니다.");
                }
                if (reader.read(0) == null) throw badRequest("손상된 이미지입니다.");
                return actual;
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException exception) {
            throw badRequest("읽을 수 없거나 손상된 이미지입니다.");
        }
    }

    public String url(String filename) { path(filename); return URL_PREFIX + filename; }

    /** 이 저장소가 만든 이미지 주소이면 파일 이름을, 외부 URL·다른 기능의 파일·이전 형식 주소이면 null을 돌려준다. */
    public String ownedFilename(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) return null;
        String filename = url.substring(URL_PREFIX.length());
        return FILE_NAME.matcher(filename).matches() ? filename : null;
    }

    public void delete(String filename) throws IOException { Files.deleteIfExists(path(filename)); }

    private Path path(String filename) {
        if (filename == null || !FILE_NAME.matcher(filename).matches()) throw notFound();
        return root.resolve(filename);
    }

    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException tooLarge() { return new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "이미지는 각각 256KB 이하여야 합니다."); }
    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "이미지를 저장할 수 없습니다. 잠시 후 다시 시도해 주세요."); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."); }

    public record StoredImage(String filename, String url) {}
}

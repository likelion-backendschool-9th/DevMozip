package com.devmozip.backend.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LocalProfileImageStorage {
    public static final int MAX_BYTES = 256 * 1024;
    private static final long MAX_PIXELS = 16_000_000;
    private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp)");
    private final Path root;
    private final String publicPrefix;

    public LocalProfileImageStorage(
            @Value("${profile.images.directory:./uploads/profile-images}") String directory,
            @Value("${profile.images.public-base-url:http://localhost:8080}") String baseUrl) {
        root = Path.of(directory).toAbsolutePath().normalize();
        URI base = URI.create(baseUrl);
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) {
            throw new IllegalArgumentException("이미지 공개 주소는 http(s) 원점이어야 합니다.");
        }
        publicPrefix = baseUrl.replaceAll("/+$", "") + "/api/v1/profile-images/";
        if (publicPrefix.length() + 41 > 255) throw new IllegalArgumentException("이미지 공개 주소가 너무 깁니다.");
    }

    public StoredImage save(MultipartFile file) {
        if (file == null || file.isEmpty()) throw badRequest("이미지 파일을 선택해 주세요.");
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException exception) { throw unavailable(); }
        if (bytes.length > MAX_BYTES) throw tooLarge();
        String extension = validateImage(bytes, file.getContentType());
        String name = UUID.randomUUID() + "." + extension;
        Path target = path(name);
        boolean created = false;
        try {
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw unavailable();
            // 원본 파일명은 저장 경로와 확장자 판단에 사용하지 않는다.
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                output.write(bytes);
            }
        } catch (IOException exception) {
            try { if (created) Files.deleteIfExists(target); } catch (IOException ignored) { /* 실패 파일 정리는 후속 점검 대상 */ }
            throw unavailable();
        }
        return new StoredImage(name, url(name));
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

    public byte[] read(String filename) {
        Path source = path(filename);
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) throw notFound();
        try (var input = Files.newInputStream(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw notFound();
            return bytes;
        } catch (IOException exception) { throw notFound(); }
    }

    public String url(String filename) { path(filename); return publicPrefix + filename; }

    public String ownedFilename(String url) {
        if (url == null || !url.startsWith(publicPrefix)) return null;
        String filename = url.substring(publicPrefix.length());
        return FILE_NAME.matcher(filename).matches() ? filename : null;
    }

    public void delete(String filename) throws IOException { Files.deleteIfExists(path(filename)); }

    private Path path(String filename) {
        if (filename == null || !FILE_NAME.matcher(filename).matches()) throw notFound();
        return root.resolve(filename);
    }

    public static String contentType(String filename) {
        return filename.endsWith(".png") ? "image/png" : filename.endsWith(".jpg") ? "image/jpeg" : "image/webp";
    }

    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException tooLarge() { return new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "이미지는 각각 256KB 이하여야 합니다."); }
    private static ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "이미지를 저장할 수 없습니다. 잠시 후 다시 시도해 주세요."); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다."); }

    public record StoredImage(String filename, String url) {}
}

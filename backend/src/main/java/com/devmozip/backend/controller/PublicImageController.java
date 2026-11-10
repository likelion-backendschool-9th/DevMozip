package com.devmozip.backend.controller;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.devmozip.backend.storage.FeedImageStorage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * nginx 없이 실행할 때(compose 기본 실행으로 3000번 직접 접속, local 프로필) 업로드 이미지(IMAGE_STORAGE 폴더)를 제공한다.
 * nginx가 있는 환경에서는 nginx가 /images/를 먼저 처리한다. nginx가 없으면 Next.js의 /images/{filename} Route Handler가 이 경로를 부른다.
 * nginx와 같이 권한 확인 없이 제공한다(docs/feed-plan.md 3절 "이미지 저장").
 */
@RestController
public class PublicImageController {
	// 기능 접두어_이름.확장자. 하위 폴더와 상위 경로는 받지 않는다.
	private static final Pattern FILE_NAME = Pattern.compile("[a-z]+_[A-Za-z0-9_-]{1,200}\\.(png|jpg|webp|gif)");
	private static final Map<String, MediaType> TYPES = Map.of(
		"png", MediaType.IMAGE_PNG,
		"jpg", MediaType.IMAGE_JPEG,
		"gif", MediaType.IMAGE_GIF,
		"webp", MediaType.parseMediaType("image/webp"));

	private final Path root;

	public PublicImageController(FeedImageStorage storage) {
		this.root = storage.root();
	}

	@GetMapping("/images/{filename}")
	public ResponseEntity<Resource> read(@PathVariable String filename) {
		if (!FILE_NAME.matcher(filename).matches()) throw notFound();
		Path path = root.resolve(filename).normalize();
		if (!path.startsWith(root) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw notFound();
		String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		return ResponseEntity.ok()
			.contentType(TYPES.get(extension))
			.cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
			.header("X-Content-Type-Options", "nosniff")
			.body(new FileSystemResource(path));
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "이미지를 찾을 수 없습니다.");
	}
}

package com.devmozip.backend.storage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 게시글 이미지 저장·삭제 (docs/feed-plan.md 3절 "이미지 저장").
 * 폴더는 환경 변수 IMAGE_STORAGE이며 다른 기능도 같은 폴더를 쓸 수 있어 파일 이름 접두어 feed_로 구분한다.
 * 파일 이름: feed_{게시글 id}_{업로드 시각 yyyyMMddHHmmss}_{image_urls 안의 순서}.{파일 내용으로 확인한 확장자}
 * DB에는 /images/{파일 이름}을 저장하고, 파일은 nginx(없으면 백엔드 PublicImageController)가 제공한다.
 */
@Component
public class FeedImageStorage {
	public static final int MAX_COUNT = 3;
	public static final int MAX_BYTES = 512 * 1024;
	public static final String URL_PREFIX = "/images/";
	public static final String INVALID_MESSAGE = "이미지는 PNG/JPEG/WebP/GIF, 각 512KB 이하, 최대 3장까지 첨부할 수 있습니다.";
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
	private static final Pattern STORED = Pattern.compile("/images/(feed_(\\d+)_\\d{14}_\\d+\\.(?:png|jpg|webp|gif))");
	private static final Logger log = LoggerFactory.getLogger(FeedImageStorage.class);

	/** 검증을 마친 업로드 파일. 확장자는 파일 내용으로 정한다. */
	public record Upload(byte[] bytes, String extension) {}

	private final Path root;

	public FeedImageStorage(@Value("${IMAGE_STORAGE:../public/images}") String directory) {
		this.root = Path.of(directory).toAbsolutePath().normalize();
	}

	public Path root() {
		return root;
	}

	/** 업로드 파일을 읽고 형식·크기를 확인한다. 게시글을 저장하기 전에 불러 잘못된 파일이면 아무것도 저장하지 않게 한다. */
	public List<Upload> read(List<MultipartFile> files) {
		List<Upload> uploads = new ArrayList<>();
		for (MultipartFile file : files == null ? List.<MultipartFile>of() : files) {
			if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) throw invalid();
			byte[] bytes;
			try (var input = file.getInputStream()) {
				bytes = input.readNBytes(MAX_BYTES + 1);
			} catch (IOException exception) {
				throw invalid();
			}
			if (bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid();
			uploads.add(new Upload(bytes, extensionOf(bytes)));
		}
		return uploads;
	}

	/**
	 * 파일을 쓰고 /images/ 경로를 돌려준다. 트랜잭션 안에서 부르면, 트랜잭션이 커밋되지 않을 때 쓴 파일을 지운다.
	 * @param firstNo 첫 파일의 image_urls 안 순서(1부터)
	 */
	public List<String> store(long postId, List<Upload> uploads, int firstNo, LocalDateTime now) {
		List<Path> written = new ArrayList<>();
		List<String> urls = new ArrayList<>();
		try {
			Files.createDirectories(root);
			if (Files.isSymbolicLink(root)) throw new IOException("이미지 폴더가 심볼릭 링크입니다.");
			for (int index = 0; index < uploads.size(); index++) {
				Upload upload = uploads.get(index);
				String name = "feed_" + postId + "_" + now.format(STAMP) + "_" + (firstNo + index) + "." + upload.extension();
				Path target = root.resolve(name);
				// 같은 이름이 있으면 덮어쓰지 않고 실패한다(같은 초에 같은 게시글을 다시 수정한 경우).
				try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
					written.add(target);
					output.write(upload.bytes());
				}
				urls.add(URL_PREFIX + name);
			}
		} catch (IOException exception) {
			deleteQuietly(written);
			log.warn("게시글 이미지를 저장하지 못했습니다. postId={}", postId, exception);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "이미지를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.");
		}
		if (TransactionSynchronizationManager.isSynchronizationActive() && !written.isEmpty()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCompletion(int status) {
					if (status != STATUS_COMMITTED) deleteQuietly(written);
				}
			});
		}
		return urls;
	}

	/** 이 게시글의 이미지 경로인지. 다른 게시글·다른 기능의 파일은 지우지 않는다. */
	public boolean isPostImage(long postId, String url) {
		Matcher matcher = url == null ? null : STORED.matcher(url);
		return matcher != null && matcher.matches() && Long.toString(postId).equals(matcher.group(2));
	}

	/** 커밋된 뒤 파일을 지운다. 트랜잭션 밖이면 바로 지운다. 파일 삭제가 실패해도 게시글 처리는 되돌리지 않는다. */
	public void deleteAfterCommit(long postId, Collection<String> urls) {
		List<Path> targets = urls.stream().filter(url -> isPostImage(postId, url))
			.map(url -> root.resolve(url.substring(URL_PREFIX.length()))).toList();
		if (targets.isEmpty()) return;
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					deleteQuietly(targets);
				}
			});
		} else {
			deleteQuietly(targets);
		}
	}

	private void deleteQuietly(List<Path> paths) {
		for (Path path : paths) {
			try {
				if (path.normalize().startsWith(root) && !Files.isSymbolicLink(path)) Files.deleteIfExists(path);
			} catch (IOException exception) {
				log.warn("게시글 이미지 파일을 지우지 못했습니다. {}", path.getFileName(), exception);
			}
		}
	}

	/** 파일 앞부분으로 형식을 정하고, 이미지 읽기 도구가 크기를 읽을 수 있는지 확인한다. */
	private static String extensionOf(byte[] bytes) {
		String extension;
		if (startsWith(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'})) extension = "png";
		else if (startsWith(bytes, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) extension = "jpg";
		else if (startsWith(bytes, "GIF87a".getBytes(StandardCharsets.US_ASCII))
			|| startsWith(bytes, "GIF89a".getBytes(StandardCharsets.US_ASCII))) extension = "gif";
		else if (bytes.length >= 12 && startsWith(bytes, "RIFF".getBytes(StandardCharsets.US_ASCII))
			&& Arrays.equals(Arrays.copyOfRange(bytes, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII))) extension = "webp";
		else throw invalid();
		try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
			var readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) throw invalid();
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				if (reader.getWidth(0) <= 0 || reader.getHeight(0) <= 0) throw invalid();
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof ResponseStatusException status) throw status;
			throw invalid();
		}
		return extension;
	}

	private static boolean startsWith(byte[] bytes, byte[] prefix) {
		return bytes.length >= prefix.length && Arrays.equals(Arrays.copyOf(bytes, prefix.length), prefix);
	}

	private static ResponseStatusException invalid() {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_MESSAGE);
	}
}

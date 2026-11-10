package com.devmozip.backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import com.devmozip.backend.entity.Message;
import com.devmozip.backend.repository.MessageRepository;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 1:1과 프로젝트 채팅이 함께 쓰는 입력 규칙, 메시지 페이지, 전송 시각 계산 */
final class MessengerRules {
	static final int MAX_BODY_LENGTH = 2000;
	static final int MAX_SIZE = 100;

	private MessengerRules() {
	}

	/** 조회한 메시지(오름차순)와 더 이전 기록의 커서 */
	record MessagePage(List<Message> messages, Long nextBefore) {
	}

	/** 파라미터가 없으면 최신 메시지, before는 이전 기록, after는 새 메시지. 결과는 항상 오름차순이다. */
	static MessagePage messagePage(MessageRepository messages, long roomId, Long before, Long after, int pageSize) {
		if (after != null) {
			return new MessagePage(messages.findAfter(roomId, after, Limit.of(pageSize)), null);
		}
		List<Message> newestFirst = before == null
			? messages.findLatest(roomId, Limit.of(pageSize + 1))
			: messages.findBefore(roomId, before, Limit.of(pageSize + 1));
		boolean hasOlder = newestFirst.size() > pageSize;
		List<Message> kept = hasOlder ? newestFirst.subList(0, pageSize) : newestFirst;
		return new MessagePage(kept.reversed(), hasOlder ? kept.getLast().getId() : null);
	}

	static void rejectBeforeAndAfter(Long before, Long after) {
		if (before != null && after != null) {
			throw badRequest("before와 after는 함께 사용할 수 없습니다.");
		}
	}

	// 방 잠금 안에서 호출한다. 읽음과 안 읽은 수가 시각 기준이라 같은 방의 이전 메시지보다 늦어야 한다.
	static LocalDateTime nextSentAt(MessageRepository messages, Clock clock, long roomId) {
		LocalDateTime now = now(clock);
		return messages.findLatestSentAt(roomId)
			.filter(latest -> !now.isAfter(latest))
			.map(latest -> latest.plus(1, ChronoUnit.MICROS))
			.orElse(now);
	}

	// DB 정밀도(마이크로초)에 맞춰 저장 전후의 값이 달라지지 않게 한다.
	static LocalDateTime now(Clock clock) {
		return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
	}

	static String validBody(String body) {
		String content = body == null ? "" : body.strip();
		if (content.isEmpty() || content.length() > MAX_BODY_LENGTH) {
			throw badRequest("메시지는 1자 이상 2,000자 이하로 입력해 주세요.");
		}
		if (content.indexOf('\0') >= 0) {
			throw badRequest("메시지에 사용할 수 없는 문자가 있습니다.");
		}
		return content;
	}

	/** 대소문자 없이 포함 여부를 찾는 LIKE 패턴. 이스케이프 문자는 '!'다. */
	static String containsPattern(String query) {
		String keyword = query == null ? "" : query.strip();
		if (keyword.indexOf('\0') >= 0) {
			throw badRequest("검색어를 확인해 주세요.");
		}
		String escaped = keyword.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return "%" + escaped + "%";
	}

	static int pageSize(Integer size, int defaultSize) {
		if (size == null) {
			return defaultSize;
		}
		if (size < 1 || size > MAX_SIZE) {
			throw badRequest("size는 1 이상 100 이하로 입력해 주세요.");
		}
		return size;
	}

	static ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	static ResponseStatusException notFound(String message) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
	}

	static ResponseStatusException conflict(String message) {
		return new ResponseStatusException(HttpStatus.CONFLICT, message);
	}

	static ResponseStatusException forbidden(String message) {
		return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
	}
}

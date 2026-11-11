package com.devmozip.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.devmozip.backend.config.JwtProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 운영 Redis(AOF)의 탈퇴 후 정리 작업. 빌링키는 기존 암호문만 저장한다. */
@Component
public class AccountWithdrawalTasks {
	private static final String PREFIX = "withdrawal:cleanup:";
	private final StringRedisTemplate redis;
	private final ObjectMapper mapper;
	private final JwtProperties jwt;
	private final boolean memory;
	private final Map<String, String> local = new ConcurrentHashMap<>();
	private final Map<String, Long> revoked = new ConcurrentHashMap<>();
	private final Map<String, String> leases = new ConcurrentHashMap<>();

	public AccountWithdrawalTasks(StringRedisTemplate redis, ObjectMapper mapper, JwtProperties jwt, Environment environment) {
		this.redis = redis; this.mapper = mapper; this.jwt = jwt;
		memory = environment.acceptsProfiles(Profiles.of("local", "test"));
	}

	public record Job(long userId, String encryptedBillingKey, List<String> imageFiles, String operationId) {
		public Job(long userId, String encryptedBillingKey, List<String> imageFiles) {
			this(userId, encryptedBillingKey, imageFiles, UUID.randomUUID().toString());
		}
	}

	public void save(Job job) {
		String json = mapper.writeValueAsString(job);
		if (memory) local.put(PREFIX + job.userId(), json);
		else redis.opsForValue().set(PREFIX + job.userId(), json);
	}

	public List<String> keys() {
		if (memory) return List.copyOf(local.keySet());
		try (var cursor = redis.scan(ScanOptions.scanOptions().match(PREFIX + "*").count(100).build())) {
			var keys = new java.util.ArrayList<String>();
			while (cursor.hasNext()) keys.add(cursor.next());
			return keys;
		}
	}

	public Job read(String key) {
		String json = memory ? local.get(key) : redis.opsForValue().get(key);
		return json == null ? null : mapper.readValue(json, Job.class);
	}

	public void removePrepared(Job job) {
		String key = PREFIX + job.userId();
		String json = mapper.writeValueAsString(job);
		if (memory) local.remove(key, json);
		else redis.execute(new DefaultRedisScript<>("if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class), List.of(key), json);
	}

	public String acquire(String key) {
		String token = UUID.randomUUID().toString();
		boolean acquired = memory ? leases.putIfAbsent(key, token) == null
			: Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("withdrawal:lease:" + key, token, Duration.ofMinutes(2)));
		return acquired ? token : null;
	}

	public void release(String key, String token) {
		if (memory) leases.remove(key, token);
		else redis.execute(new DefaultRedisScript<>("if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class),
			List.of("withdrawal:lease:" + key), token);
	}

	/** 이전 형식(회원 ID 없는 JWT)도 같은 이메일 재가입 계정에 적용되지 않게 한다. */
	public void revokeLegacy(String email) {
		String key = fingerprint(email);
		Duration ttl = Duration.ofMillis(Math.max(jwt.getAccessTokenExpiration(), jwt.getRefreshTokenExpiration())).plusSeconds(60);
		if (memory) revoked.put(key, System.currentTimeMillis() + ttl.toMillis());
		else redis.opsForValue().set(key, "1", ttl);
	}

	public boolean legacyRevoked(String email) {
		String key = fingerprint(email);
		if (!memory) return Boolean.TRUE.equals(redis.hasKey(key));
		Long expiry = revoked.get(key);
		if (expiry == null) return false;
		if (expiry > System.currentTimeMillis()) return true;
		revoked.remove(key, expiry);
		return false;
	}

	private static String fingerprint(String email) {
		try {
			return "withdrawal:legacy:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e); }
	}
}

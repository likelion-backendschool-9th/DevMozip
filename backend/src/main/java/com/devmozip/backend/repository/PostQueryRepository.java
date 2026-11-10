package com.devmozip.backend.repository;

import java.sql.Types;
import java.util.List;
import java.util.Locale;

import com.devmozip.backend.entity.enums.PostCategory;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 피드 목록(8.1~8.3)의 검색·정렬. local 프로필(H2)에서도 같은 결과가 나오도록 PostgreSQL 전용 함수·연산자를 쓰지 않는다
 * (docs/feed-plan.md 3절 "H2 호환"). 한 페이지의 게시글 id만 돌려주고, 내용은 PostRepository로 읽는다.
 */
@Repository
public class PostQueryRepository {
	/** 정렬. ORDER BY 절은 이 값에서만 만들고 요청 값으로 만들지 않는다. */
	public enum Sort {
		// x = (id × 곱하는 값 + 더하는 값) mod p를 다시 제곱해 섞는다. 한 번만 곱하면 seed에 따라 작은 id들이 차례대로 남는다.
		// x < 2^31이라 x × x도 bigint 안이다. H2는 MOD 결과를 INTEGER로 보므로 BIGINT로 바꾼 뒤 곱한다. 제곱 값이 같으면 id 순서로 정한다.
		RECOMMENDED(" ORDER BY MOD(CAST(MOD(p.id * :mixMultiplier + :mixOffset, 2147483647) AS BIGINT)"
			+ " * CAST(MOD(p.id * :mixMultiplier + :mixOffset, 2147483647) AS BIGINT), 2147483647), p.id"),
		POPULAR(" ORDER BY (SELECT count(*) FROM likes l WHERE l.post_id = p.id) DESC, p.created_at DESC, p.id DESC"),
		VIEWS(" ORDER BY p.view_count DESC, p.created_at DESC, p.id DESC"),
		LATEST(" ORDER BY p.created_at DESC, p.id DESC");

		private final String orderBy;

		Sort(String orderBy) {
			this.orderBy = orderBy;
		}
	}

	/**
	 * @param category 없으면 전체
	 * @param terms 소문자로 바꾸기 전의 검색어 단어. 모든 단어가 제목·내용·기술 스택·관심 분야 중 한 곳에 있어야 한다
	 * @param followerId 팔로잉 피드면 요청자 id. 전체·추천 피드면 null
	 * @param seed 추천순의 섞는 값. 다음 페이지에도 같은 값을 보내야 순서가 유지된다
	 */
	public record Condition(PostCategory category, List<String> terms, Long followerId, Sort sort, int seed) {}

	public record Page(List<Long> ids, boolean hasNext, long totalCount) {}

	private final NamedParameterJdbcTemplate jdbc;

	public PostQueryRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Page find(Condition condition, int page, int size) {
		var params = new MapSqlParameterSource();
		// 피드에는 삭제·숨김이 아닌 원본 글만, 이용 가능한 회원(ACTIVE·WARNED)의 글만 나온다.
		StringBuilder from = new StringBuilder(" FROM posts p JOIN users a ON a.id = p.author_id")
			.append(" WHERE p.deleted_at IS NULL AND p.visibility = 'VISIBLE' AND p.type = 'POST'")
			.append(" AND a.status IN ('ACTIVE', 'WARNED')");
		if (condition.category() != null) {
			params.addValue("category", condition.category().name());
			from.append(" AND p.category = :category");
		}
		if (condition.followerId() != null) {
			params.addValue("followerId", condition.followerId(), Types.BIGINT);
			from.append(" AND EXISTS (SELECT 1 FROM follows f WHERE f.follower_id = :followerId AND f.following_id = p.author_id)");
		}
		for (int index = 0; index < condition.terms().size(); index++) {
			String name = "term" + index;
			params.addValue(name, "%" + escapeLike(condition.terms().get(index).toLowerCase(Locale.ROOT)) + "%");
			// JSON 배열은 문자열로 바꿔 비교한다. 두 DB 모두 원소 문자열을 그대로 담은 JSON 문자열을 돌려준다.
			from.append(" AND (").append(contains("p.title", name))
				.append(" OR ").append(contains("p.content", name))
				.append(" OR ").append(contains("CAST(p.tech_stacks AS VARCHAR(32000))", name))
				.append(" OR ").append(contains("CAST(p.interests AS VARCHAR(32000))", name)).append(")");
		}
		Long total = jdbc.queryForObject("SELECT count(*)" + from, params, Long.class);

		// 추천순: 곱하는 값과 더하는 값을 seed에서 정해 seed마다 다른 순서를 만든다. seed를 큰 수와 곱해 흩어 놓아
		// 1만 다른 seed도 곱하는 값이 크게 달라진다. 곱하는 값은 법(2147483647)보다 작은 양수라 서로 다른 id는 서로 다른 값이 되고,
		// id가 약 40억을 넘기 전까지 bigint 안에서 계산된다.
		params.addValue("mixMultiplier", 1 + Math.floorMod(condition.seed() * 2_654_435_761L + 40_503L, 2_147_483_646L))
			.addValue("mixOffset", Math.floorMod(condition.seed() * 40_503L, 2_147_483_647L))
			.addValue("limit", size + 1)
			.addValue("offset", (long) page * size);
		List<Long> ids = jdbc.queryForList("SELECT p.id" + from + condition.sort().orderBy + " LIMIT :limit OFFSET :offset",
			params, Long.class);
		boolean hasNext = ids.size() > size;
		return new Page(hasNext ? List.copyOf(ids.subList(0, size)) : ids, hasNext, total == null ? 0 : total);
	}

	private static String contains(String column, String parameter) {
		return "lower(COALESCE(" + column + ", '')) LIKE :" + parameter + " ESCAPE '!'";
	}

	private static String escapeLike(String value) {
		return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
	}
}

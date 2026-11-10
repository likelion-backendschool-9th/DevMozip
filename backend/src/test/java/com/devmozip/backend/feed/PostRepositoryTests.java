package com.devmozip.backend.feed;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

import com.devmozip.backend.entity.Bookmark;
import com.devmozip.backend.entity.Comment;
import com.devmozip.backend.entity.Follow;
import com.devmozip.backend.entity.Like;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.entity.Post;
import com.devmozip.backend.entity.Report;
import com.devmozip.backend.entity.enums.PostCategory;
import com.devmozip.backend.entity.enums.PostType;
import com.devmozip.backend.entity.enums.PostVisibility;
import com.devmozip.backend.repository.BookmarkRepository;
import com.devmozip.backend.repository.CommentRepository;
import com.devmozip.backend.repository.FollowRepository;
import com.devmozip.backend.repository.LikeRepository;
import com.devmozip.backend.repository.PostQueryRepository;
import com.devmozip.backend.repository.PostQueryRepository.Condition;
import com.devmozip.backend.repository.PostQueryRepository.Sort;
import com.devmozip.backend.repository.PostRepository;
import com.devmozip.backend.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * posts·comments 매핑과 피드 목록 SQL. PostgreSQL과 local(H2)에서 같은 결과가 나와야 한다 (docs/feed-plan.md 3절).
 * API 테스트와 같은 준비(FeedApiTestSupport)를 써서 같은 Spring 컨텍스트를 함께 쓴다. 테이블 정리와 회원 생성은 그쪽에 있다.
 */
abstract class PostRepositoryTests extends FeedApiTestSupport {
	static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 10, 9, 0);

	@Autowired PostRepository posts;
	@Autowired PostQueryRepository queries;
	@Autowired CommentRepository comments;
	@Autowired LikeRepository likes;
	@Autowired BookmarkRepository bookmarks;
	@Autowired ReportRepository reports;
	@Autowired FollowRepository follows;
	@Autowired PlatformTransactionManager transactionManager;
	TransactionTemplate tx;

	@BeforeEach
	void transactions() {
		tx = new TransactionTemplate(transactionManager);
	}

	Post write(Member author, PostCategory category, String title, String content, List<String> stacks,
		List<String> interests, int minutes) {
		return posts.save(Post.write(author, category, title, content, stacks, interests, BASE.plusMinutes(minutes)));
	}

	Post write(Member author, String title, int minutes) {
		return write(author, PostCategory.INFO, title, "본문 " + title, List.of(), List.of(), minutes);
	}

	/** 수정 쿼리를 트랜잭션 안에서 실행하고 바뀐 행 수를 돌려준다. */
	int changed(IntSupplier work) {
		Integer rows = tx.execute(status -> work.getAsInt());
		return rows == null ? -1 : rows;
	}

	List<Long> ids(Condition condition) {
		return queries.find(condition, 0, 100).ids();
	}

	Condition latest() {
		return new Condition(null, List.of(), null, Sort.LATEST, 0);
	}

	Condition search(String... terms) {
		return new Condition(null, List.of(terms), null, Sort.LATEST, 0);
	}

	@Test
	void storesPostColumnsAndJsonArrays() {
		Member author = member("writer");
		Post saved = write(author, PostCategory.FEEDBACK, "첫 API 설계", "피드백 부탁드려요",
			List.of("Spring Boot", "타입스크립트"), List.of("교육"), 0);
		tx.executeWithoutResult(status -> posts.findById(saved.getId()).orElseThrow()
			.replaceImageUrls(List.of("/images/feed_1_20261110090000_1.png")));

		Post found = posts.findLive(saved.getId()).orElseThrow();
		assertEquals(PostType.POST, found.getType());
		assertEquals(PostCategory.FEEDBACK, found.getCategory());
		assertEquals(PostVisibility.VISIBLE, found.getVisibility());
		assertEquals("첫 API 설계", found.getTitle());
		assertEquals(List.of("Spring Boot", "타입스크립트"), found.getTechStacks());
		assertEquals(List.of("교육"), found.getInterests());
		assertEquals(List.of("/images/feed_1_20261110090000_1.png"), found.getImageUrls());
		assertEquals(0, found.getViewCount());
		assertNull(found.getOriginalPost());
		assertEquals(author.getId(), found.getAuthor().getId());
		assertEquals(BASE, found.getCreatedAt());
		assertFalse(found.isEdited());
		// 목록 검색은 JSON을 문자열로 바꿔 비교하므로, 한글 원소가 escape 없이 그대로 나와야 한다.
		String stacks = jdbc.queryForObject("SELECT CAST(tech_stacks AS VARCHAR(1000)) FROM posts WHERE id = ?", String.class, saved.getId());
		assertTrue(stacks.contains("\"타입스크립트\""), stacks);
	}

	@Test
	void repostKeepsOriginalAndCategoryWithoutTitleOrTags() {
		Member author = member("writer");
		Member reader = member("reader");
		Post original = write(author, PostCategory.PROMO, "홍보 글", "본문", List.of("React"), List.of(), 0);
		Post repost = posts.save(Post.repost(reader, original, null, BASE.plusMinutes(1)));

		Post found = posts.findLive(repost.getId()).orElseThrow();
		assertEquals(PostType.REPOST, found.getType());
		assertEquals(PostCategory.PROMO, found.getCategory());
		assertEquals(original.getId(), found.getOriginalPost().getId());
		assertEquals(author.getId(), found.getOriginalPost().getAuthor().getId());
		assertNull(found.getTitle());
		assertNull(found.getContent());
		assertEquals(List.of(), found.getTechStacks());
		assertEquals(1, posts.findLiveReposts(reader.getId(), original.getId()).size());
		assertEquals(1, posts.findLiveRepostsOf(reader.getId(), List.of(original.getId(), 999L)).size());

		tx.executeWithoutResult(status -> posts.findById(repost.getId()).orElseThrow().softDelete(BASE.plusMinutes(2)));
		assertTrue(posts.findLive(repost.getId()).isEmpty());
		assertTrue(posts.findLiveReposts(reader.getId(), original.getId()).isEmpty());
	}

	@Test
	void viewCountIncreasesOnlyForLivePosts() {
		Post post = write(member("writer"), "조회", 0);
		assertEquals(1, changed(() -> posts.increaseViewCount(post.getId())));
		assertEquals(1, changed(() -> posts.increaseViewCount(post.getId())));
		assertEquals(2, posts.findById(post.getId()).orElseThrow().getViewCount());

		tx.executeWithoutResult(status -> posts.findById(post.getId()).orElseThrow().softDelete(BASE.plusMinutes(5)));
		assertEquals(0, changed(() -> posts.increaseViewCount(post.getId())));
		assertEquals(2, posts.findById(post.getId()).orElseThrow().getViewCount());
	}

	@Test
	void commentsAreListedInOrderAndCountedWithoutDeleted() {
		Member writer = member("writer");
		Member reader = member("reader");
		Post post = write(writer, "댓글", 0);
		Comment first = comments.save(new Comment(post, reader, "첫 댓글", BASE.plusMinutes(1)));
		Comment second = comments.save(new Comment(post, writer, "둘째 댓글", BASE.plusMinutes(2)));
		Comment removed = comments.save(new Comment(post, reader, "지울 댓글", BASE.plusMinutes(3)));
		tx.executeWithoutResult(status -> comments.findById(removed.getId()).orElseThrow().softDelete(BASE.plusMinutes(4)));

		assertEquals(List.of(first.getId(), second.getId()), comments.findLiveByPost(post.getId()).stream().map(Comment::getId).toList());
		List<Object[]> counts = comments.countLiveByPosts(List.of(post.getId()));
		assertEquals(1, counts.size());
		assertEquals(2L, ((Number) counts.get(0)[1]).longValue());
		assertTrue(tx.execute(status -> comments.findLiveForUpdate(removed.getId())).isEmpty());
		assertTrue(tx.execute(status -> comments.findLiveForUpdate(first.getId())).isPresent());
	}

	@Test
	void likesAndBookmarksUseCommonTablesWithUniquePostRows() {
		Member writer = member("writer");
		Member reader = member("reader");
		Post post = write(writer, "반응", 0);
		likes.saveAndFlush(Like.forPost(reader.getId(), post.getId(), BASE));
		bookmarks.saveAndFlush(Bookmark.forPost(reader.getId(), post.getId(), BASE));

		assertTrue(likes.existsByUserIdAndPostId(reader.getId(), post.getId()));
		assertFalse(likes.existsByUserIdAndPostId(writer.getId(), post.getId()));
		assertEquals(1, likes.countByPostId(post.getId()));
		assertEquals(List.of(post.getId()), likes.likedPostIds(reader.getId(), List.of(post.getId())));
		assertEquals(List.of(post.getId()), bookmarks.bookmarkedPostIds(reader.getId(), List.of(post.getId())));
		assertNull(jdbc.queryForObject("SELECT project_id FROM likes", Long.class));
		assertNull(jdbc.queryForObject("SELECT project_id FROM bookmarks", Long.class));

		assertThrows(DataIntegrityViolationException.class,
			() -> likes.saveAndFlush(Like.forPost(reader.getId(), post.getId(), BASE)));
		assertThrows(DataIntegrityViolationException.class,
			() -> bookmarks.saveAndFlush(Bookmark.forPost(reader.getId(), post.getId(), BASE)));

		assertEquals(1, changed(() -> likes.removePost(reader.getId(), post.getId())));
		assertEquals(1, changed(() -> bookmarks.removePost(reader.getId(), post.getId())));
		assertEquals(0, likes.countByPostId(post.getId()));
	}

	@Test
	void postAndCommentReportsStartAsAwating() {
		Member writer = member("writer");
		Member reader = member("reader");
		Post post = write(writer, "신고", 0);
		Comment comment = comments.save(new Comment(post, writer, "댓글", BASE));
		reports.saveAndFlush(Report.ofPost(reader, post.getId(), "허위 정보", BASE));
		reports.saveAndFlush(Report.ofComment(reader, comment.getId(), "기타", BASE));

		assertEquals(List.of("AWATING", "AWATING"), jdbc.queryForList("SELECT status FROM reports", String.class));
		assertTrue(reports.existsByReporter_IdAndPostIdAndCommentIdIsNull(reader.getId(), post.getId()));
		assertTrue(reports.existsByReporter_IdAndCommentId(reader.getId(), comment.getId()));
		assertFalse(reports.existsByReporter_IdAndPostIdAndCommentIdIsNull(writer.getId(), post.getId()));
		assertEquals(List.of(post.getId()), reports.reportedPostIds(reader.getId(), List.of(post.getId())));
		assertEquals(List.of(comment.getId()), reports.reportedCommentIds(reader.getId(), List.of(comment.getId())));
		assertNull(jdbc.queryForObject("SELECT reported_user_id FROM reports WHERE post_id IS NOT NULL", Long.class));
	}

	@Test
	void feedListsOnlyVisibleOriginalPostsOfUsableMembers() {
		Member writer = member("writer");
		Member warned = member("warned", Member.MemberStatus.WARNED);
		Member suspended = member("suspended", Member.MemberStatus.SUSPENDED);
		Post visible = write(writer, "공개", 0);
		Post fromWarned = write(warned, "경고 회원 글", 1);
		write(suspended, "정지 회원 글", 2);
		Post hidden = write(writer, "숨김", 3);
		Post deleted = write(writer, "삭제", 4);
		posts.save(Post.repost(warned, visible, "의견", BASE.plusMinutes(5)));
		jdbc.update("UPDATE posts SET visibility = 'HIDDEN' WHERE id = ?", hidden.getId());
		Post restricted = write(writer, "관리자 숨김", 6);
		jdbc.update("UPDATE posts SET visibility = 'RESTRICTED' WHERE id = ?", restricted.getId());
		tx.executeWithoutResult(status -> posts.findById(deleted.getId()).orElseThrow().softDelete(BASE.plusMinutes(7)));

		assertEquals(List.of(fromWarned.getId(), visible.getId()), ids(latest()));
	}

	@Test
	void categoryAndFollowingFiltersCombine() {
		Member me = member("me");
		Member followed = member("followed");
		Member other = member("other");
		Post info = write(followed, PostCategory.INFO, "정보", "본문", List.of(), List.of(), 0);
		Post feedback = write(followed, PostCategory.FEEDBACK, "피드백", "본문", List.of(), List.of(), 1);
		Post otherInfo = write(other, PostCategory.INFO, "남의 정보", "본문", List.of(), List.of(), 2);
		write(me, PostCategory.INFO, "내 글", "본문", List.of(), List.of(), 3);
		follows.save(Follow.create(me, followed, BASE));

		assertEquals(List.of(feedback.getId(), info.getId()), ids(new Condition(null, List.of(), me.getId(), Sort.LATEST, 0)));
		assertEquals(List.of(info.getId()), ids(new Condition(PostCategory.INFO, List.of(), me.getId(), Sort.LATEST, 0)));
		assertEquals(3, ids(new Condition(PostCategory.INFO, List.of(), null, Sort.LATEST, 0)).size());
		assertTrue(ids(new Condition(PostCategory.INFO, List.of(), null, Sort.LATEST, 0)).contains(otherInfo.getId()));
		assertEquals(List.of(), ids(new Condition(null, List.of(), other.getId(), Sort.LATEST, 0)));
	}

	@Test
	void searchMatchesEveryTermInTitleContentStacksOrInterestsIgnoringCase() {
		Member writer = member("writer");
		Post react = write(writer, PostCategory.INFO, "React 컴포넌트 분리", "재사용 기준", List.of("TypeScript"), List.of("생산성"), 0);
		Post spring = write(writer, PostCategory.INFO, "API 설계", "에러 처리 100% 정리", List.of("Spring Boot"), List.of("교육"), 1);
		Post korean = write(writer, PostCategory.INFO, "주말 회고", "작은 성취", List.of("타입스크립트"), List.of("커뮤니티"), 2);

		assertEquals(List.of(react.getId()), ids(search("react")));
		assertEquals(List.of(react.getId()), ids(search("typescript")));
		assertEquals(List.of(korean.getId()), ids(search("타입스크립트")));
		assertEquals(List.of(spring.getId()), ids(search("spring", "교육")));
		assertEquals(List.of(korean.getId()), ids(search("커뮤니티", "성취")));
		assertEquals(List.of(), ids(search("react", "교육")));
		// LIKE 특수 문자는 문자 그대로 찾는다.
		assertEquals(List.of(spring.getId()), ids(search("100%")));
		assertEquals(List.of(), ids(search("_")));
		assertEquals(List.of(), ids(search("!")));
	}

	@Test
	void sortsByViewsAndLikesWithLatestAsTieBreaker() {
		Member writer = member("writer");
		Member reader = member("reader");
		Member another = member("another");
		Post oldest = write(writer, "오래된 글", 0);
		Post middle = write(writer, "중간 글", 1);
		Post newest = write(writer, "최신 글", 2);
		jdbc.update("UPDATE posts SET view_count = 5 WHERE id = ?", oldest.getId());
		jdbc.update("UPDATE posts SET view_count = 5 WHERE id = ?", middle.getId());
		likes.save(Like.forPost(reader.getId(), middle.getId(), BASE));
		likes.save(Like.forPost(another.getId(), middle.getId(), BASE));
		likes.save(Like.forPost(reader.getId(), oldest.getId(), BASE));

		assertEquals(List.of(newest.getId(), middle.getId(), oldest.getId()), ids(latest()));
		assertEquals(List.of(middle.getId(), oldest.getId(), newest.getId()),
			ids(new Condition(null, List.of(), null, Sort.VIEWS, 0)));
		assertEquals(List.of(middle.getId(), oldest.getId(), newest.getId()),
			ids(new Condition(null, List.of(), null, Sort.POPULAR, 0)));
	}

	@Test
	void recommendedOrderIsStablePerSeedAndChangesWithSeed() {
		Member writer = member("writer");
		List<Long> written = new ArrayList<>();
		for (int i = 0; i < 12; i++) written.add(write(writer, "글 " + i, i).getId());

		Condition seed7 = new Condition(null, List.of(), null, Sort.RECOMMENDED, 7);
		List<Long> first = ids(seed7);
		assertEquals(first, ids(seed7));
		assertEquals(written.stream().sorted().toList(), first.stream().sorted().toList());
		assertNotEquals(first, ids(new Condition(null, List.of(), null, Sort.RECOMMENDED, 123456)));
		// 섞은 순서가 작성 순서(오름차순·내림차순)와 같아지지 않는다.
		List<Long> ascending = written.stream().sorted().toList();
		for (int seed : new int[]{0, 1, 7, 42, 777, 123456, 999_999}) {
			List<Long> order = ids(new Condition(null, List.of(), null, Sort.RECOMMENDED, seed));
			assertNotEquals(ascending, order, "seed " + seed);
			assertNotEquals(ascending.reversed(), order, "seed " + seed);
		}
		// 가까운 seed도 다른 순서가 된다(화면은 세션마다 0~999999 사이의 seed를 만든다).
		for (int[] pair : new int[][]{{42, 43}, {42, 777}, {0, 1}, {999_998, 999_999}}) {
			assertNotEquals(ids(new Condition(null, List.of(), null, Sort.RECOMMENDED, pair[0])),
				ids(new Condition(null, List.of(), null, Sort.RECOMMENDED, pair[1])), pair[0] + " vs " + pair[1]);
		}

		// 같은 seed의 페이지를 이어 붙이면 한 번에 읽은 순서와 같다.
		List<Long> paged = new ArrayList<>();
		PostQueryRepository.Page page = queries.find(seed7, 0, 5);
		assertTrue(page.hasNext());
		assertEquals(12, page.totalCount());
		paged.addAll(page.ids());
		paged.addAll(queries.find(seed7, 1, 5).ids());
		PostQueryRepository.Page last = queries.find(seed7, 2, 5);
		assertFalse(last.hasNext());
		paged.addAll(last.ids());
		assertEquals(first, paged);
	}
}

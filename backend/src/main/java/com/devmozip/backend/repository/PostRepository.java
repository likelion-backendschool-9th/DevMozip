package com.devmozip.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.devmozip.backend.entity.Post;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 게시글 조회·잠금·조회수. 목록 검색과 정렬은 PostQueryRepository가 맡는다. */
public interface PostRepository extends JpaRepository<Post, Long> {
	/** 삭제되지 않은 게시글과 작성자, 퍼간 글이면 원본과 원본 작성자를 함께 읽는다. */
	@Query("select p from Post p join fetch p.author left join fetch p.originalPost o left join fetch o.author"
		+ " where p.id = :id and p.deletedAt is null")
	Optional<Post> findLive(@Param("id") long id);

	/** 수정·삭제·반응·퍼가기·신고를 같은 게시글끼리 차례로 처리한다. 외부 조인과 함께 잠글 수 없어 연관 엔티티는 따로 읽는다. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Post p where p.id = :id and p.deletedAt is null")
	Optional<Post> findLiveForUpdate(@Param("id") long id);

	/** 목록 화면의 한 페이지를 읽는다. 순서는 호출하는 쪽이 id 목록 순서로 맞춘다. */
	@Query("select p from Post p join fetch p.author left join fetch p.originalPost o left join fetch o.author where p.id in :ids")
	List<Post> findAllWithAuthors(@Param("ids") Collection<Long> ids);

	/** 동시 요청에서도 증가가 빠지지 않도록 DB에서 1을 더한다. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :id and p.deletedAt is null")
	int increaseViewCount(@Param("id") long id);

	/** 요청자가 원본을 퍼간 글. 삭제한 퍼간 글은 빼므로 취소한 뒤 다시 퍼갈 수 있다. */
	@Query("select p from Post p where p.author.id = :authorId and p.originalPost.id = :originalId"
		+ " and p.type = com.devmozip.backend.entity.enums.PostType.REPOST and p.deletedAt is null")
	List<Post> findLiveReposts(@Param("authorId") long authorId, @Param("originalId") long originalId);

	/** 목록의 원본마다 요청자가 퍼간 글이 있는지 한 번에 찾는다. */
	@Query("select p from Post p where p.author.id = :authorId and p.originalPost.id in :originalIds"
		+ " and p.type = com.devmozip.backend.entity.enums.PostType.REPOST and p.deletedAt is null")
	List<Post> findLiveRepostsOf(@Param("authorId") long authorId, @Param("originalIds") Collection<Long> originalIds);
}

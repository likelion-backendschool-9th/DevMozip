package com.devmozip.backend.repository;

import com.devmozip.backend.entity.Comment;
import com.devmozip.backend.entity.Post;
import com.devmozip.backend.entity.enums.PostVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 관리자 게시글·댓글 목록 (WBS 14.6). 삭제된 글과 댓글은 뺀다. 작성자 숨김·관리자 숨김 글은 포함한다.
 * 검색어는 AdminPaging.keyword로 소문자·이스케이프한 값이다.
 */
public interface AdminPostQueryRepository extends Repository<Post, Long> {
	@Query(value = """
		select p from Post p join fetch p.author
		where p.deletedAt is null
		and (:visibility is null or p.visibility = :visibility)
		and (:q is null or lower(p.title) like :q escape '\\' or lower(p.content) like :q escape '\\')
		order by p.id desc""",
		countQuery = """
		select count(p) from Post p
		where p.deletedAt is null
		and (:visibility is null or p.visibility = :visibility)
		and (:q is null or lower(p.title) like :q escape '\\' or lower(p.content) like :q escape '\\')""")
	Page<Post> searchPosts(@Param("q") String q, @Param("visibility") PostVisibility visibility, Pageable pageable);

	@Query(value = """
		select c from Comment c join fetch c.author join fetch c.post p
		where c.deletedAt is null and p.deletedAt is null
		and (:q is null or lower(c.content) like :q escape '\\')
		order by c.id desc""",
		countQuery = """
		select count(c) from Comment c join c.post p
		where c.deletedAt is null and p.deletedAt is null
		and (:q is null or lower(c.content) like :q escape '\\')""")
	Page<Comment> searchComments(@Param("q") String q, Pageable pageable);
}

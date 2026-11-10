package com.devmozip.backend.service;

import java.util.List;

import com.devmozip.backend.dto.feed.FeedPostRequest;
import com.devmozip.backend.dto.feed.FeedPostResponse;

import org.springframework.web.multipart.MultipartFile;

/** 게시글 작성·수정·삭제와 퍼가기 (WBS 8.4, 8.6, 8.13). 요청자는 인증 정보에서 찾는다. */
public interface FeedService {
	/** 8.4 작성. 이미지는 0~3장 */
	FeedPostResponse create(FeedPostRequest request, List<MultipartFile> images);

	/** 8.6 수정. 퍼간 글이면 의견(content)만 바꾼다. 공개 범위는 바꾸지 않는다. */
	FeedPostResponse update(long id, FeedPostRequest request, List<MultipartFile> images);

	/** 8.6 논리 삭제. 이미지 파일은 지운다. */
	void delete(long id);

	/** 8.13 원본을 퍼간 새 글을 만든다. 같은 원본은 한 번만 퍼갈 수 있다. */
	FeedPostResponse repost(long originalId, String opinion);

	/** 8.13 원본에 대해 요청자가 퍼간 글을 삭제한다. 없으면 그대로 끝난다. */
	void cancelRepost(long originalId);
}

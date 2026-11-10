package com.devmozip.backend.dto.feed;

import java.util.List;

import com.devmozip.backend.utils.StrictStringDeserializer;

import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * 게시글 작성(8.4)·수정(8.6)의 JSON 파트("post"). 이미지 파일은 같은 요청의 "images" 파트로 받는다.
 * 퍼간 글을 수정할 때는 content(의견)만 쓴다. keepImageUrls는 수정 때 남길 기존 이미지 경로이며 이 순서대로 앞에 둔다.
 * 값 검증은 Service가 화면과 같은 안내 문구로 한다.
 */
public record FeedPostRequest(
	@JsonDeserialize(using = StrictStringDeserializer.class) String category,
	@JsonDeserialize(using = StrictStringDeserializer.class) String title,
	@JsonDeserialize(using = StrictStringDeserializer.class) String content,
	@JsonDeserialize(contentUsing = StrictStringDeserializer.class) List<String> techStacks,
	@JsonDeserialize(contentUsing = StrictStringDeserializer.class) List<String> interests,
	@JsonDeserialize(contentUsing = StrictStringDeserializer.class) List<String> keepImageUrls
) {}

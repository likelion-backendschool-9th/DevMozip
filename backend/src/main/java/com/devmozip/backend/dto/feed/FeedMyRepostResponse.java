package com.devmozip.backend.dto.feed;

/** 요청자가 이 원본을 퍼간 글. 퍼가기 관리 창에서 의견 수정(8.6)과 퍼가기 취소(8.13)에 쓴다. */
public record FeedMyRepostResponse(long id, String content) {}

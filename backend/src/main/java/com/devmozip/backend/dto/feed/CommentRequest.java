package com.devmozip.backend.dto.feed;

import com.devmozip.backend.utils.StrictStringDeserializer;

import tools.jackson.databind.annotation.JsonDeserialize;

/** 댓글 작성(8.10)·수정(8.11). 앞뒤 공백을 지운 1~1,000자다. */
public record CommentRequest(@JsonDeserialize(using = StrictStringDeserializer.class) String content) {}

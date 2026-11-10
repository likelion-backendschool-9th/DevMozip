package com.devmozip.backend.dto.feed;

import com.devmozip.backend.utils.StrictStringDeserializer;

import tools.jackson.databind.annotation.JsonDeserialize;

/** 게시글·댓글 신고(8.15·8.16). reason은 PostReportReason의 문구 중 하나다. 추가 설명은 받지 않는다(목업과 같다). */
public record FeedReportRequest(@JsonDeserialize(using = StrictStringDeserializer.class) String reason) {}

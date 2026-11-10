package com.devmozip.backend.dto.feed;

import com.devmozip.backend.utils.StrictStringDeserializer;

import tools.jackson.databind.annotation.JsonDeserialize;

/** 퍼가기(8.13) 요청. content는 의견이며 없어도 된다(1,000자 이내). */
public record FeedRepostRequest(@JsonDeserialize(using = StrictStringDeserializer.class) String content) {}

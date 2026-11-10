package com.devmozip.backend.dto.notification;

/** 읽음 변경 요청. 본문이 없으면 읽음이다. 본문을 보내면 read에 true(읽음) 또는 false(안 읽음)를 넣는다. */
public record NotificationReadRequest(Boolean read) {}

package com.devmozip.backend.dto.message;

import java.util.List;

public record ChatDetailResponse(
	long id,
	ChatPeerResponse peer,
	boolean canSend,
	Long peerLastReadMessageId,
	List<ChatMessageResponse> messages,
	Long nextBefore
) {
}

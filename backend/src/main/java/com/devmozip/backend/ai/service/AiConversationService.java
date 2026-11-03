package com.devmozip.backend.ai.service;

import com.devmozip.backend.ai.dto.ConversationDetailResponse;
import com.devmozip.backend.ai.dto.ConversationResponse;
import com.devmozip.backend.ai.dto.CreateConversationRequest;
import com.devmozip.backend.ai.dto.CreateMessageRequest;
import com.devmozip.backend.ai.dto.MessageResponse;
import java.util.List;

public interface AiConversationService {
	ConversationResponse create(CreateConversationRequest request);
	List<ConversationResponse> list();
	ConversationDetailResponse detail(long id);
	MessageResponse addMessage(long id, CreateMessageRequest request);
	void delete(long id);
}

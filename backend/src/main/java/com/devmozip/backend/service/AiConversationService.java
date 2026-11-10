package com.devmozip.backend.service;

import com.devmozip.backend.dto.ai.ConversationDetailResponse;
import com.devmozip.backend.dto.ai.ConversationResponse;
import com.devmozip.backend.dto.ai.CreateConversationRequest;
import com.devmozip.backend.dto.ai.CreateMessageRequest;
import com.devmozip.backend.dto.ai.MessageResponse;
import com.devmozip.backend.dto.ai.UpdateConditionsRequest;
import com.devmozip.backend.dto.ai.ConversationConditionsResponse;
import java.util.List;

public interface AiConversationService {
	ConversationResponse create(CreateConversationRequest request);
	List<ConversationResponse> list();
	ConversationDetailResponse detail(long id);
	MessageResponse addMessage(long id, CreateMessageRequest request);
	ConversationConditionsResponse replaceConditions(long id, UpdateConditionsRequest request);
	void delete(long id);
}

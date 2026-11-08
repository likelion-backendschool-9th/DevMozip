package com.devmozip.backend.dto.message;

import com.devmozip.backend.entity.ChatMember;
import com.devmozip.backend.entity.Member;

/**
 * 프로젝트 채팅 메시지의 작성자. memberId는 같은 응답의 members[].id와 같은 참여 행 ID로,
 * 태그가 없는 회원도 구분해 읽음 수를 계산하는 데 쓴다. tag와 nickname은 아직 설정하지 않은 회원이면 null이다.
 */
public record ChatSenderResponse(long memberId, String tag, String nickname) {
	public static ChatSenderResponse from(ChatMember sender) {
		Member member = sender.getMember();
		return new ChatSenderResponse(sender.getId(), member.getTag(), member.getNickname());
	}
}

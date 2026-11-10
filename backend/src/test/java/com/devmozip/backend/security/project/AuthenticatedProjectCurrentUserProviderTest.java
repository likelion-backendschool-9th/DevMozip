package com.devmozip.backend.security.project;

import java.util.List;
import java.util.Optional;
import com.devmozip.backend.entity.Member;
import com.devmozip.backend.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticatedProjectCurrentUserProviderTest {
	private final MemberRepository members=mock(MemberRepository.class);
	private final ProjectCurrentUserProvider provider=new AuthenticatedProjectCurrentUserProvider(members);
	@AfterEach void clear(){SecurityContextHolder.clearContext();}
	private void signedIn(String email){SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));}
	@Test void identifiesMemberFromVerifiedEmailAndChecksEachCall(){
		Member member=mock(Member.class);when(member.getId()).thenReturn(5L);when(member.getStatus()).thenReturn(Member.MemberStatus.ACTIVE);
		when(members.findByEmail("premium@devmozip.demo")).thenReturn(Optional.of(member));signedIn("premium@devmozip.demo");
		assertEquals(5,provider.getCurrentUserId());
		when(member.getStatus()).thenReturn(Member.MemberStatus.SUSPENDED);assertThrows(LockedException.class,provider::getCurrentUserId);
	}
	@Test void rejectsAbsentAnonymousAndUnauthenticatedIdentity(){
		assertThrows(InsufficientAuthenticationException.class,provider::getCurrentUserId);
		SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key","anonymousUser",List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
		assertThrows(InsufficientAuthenticationException.class,provider::getCurrentUserId);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("member@devmozip.demo",null));
		assertThrows(InsufficientAuthenticationException.class,provider::getCurrentUserId);verifyNoInteractions(members);
	}
	@Test void rejectsDeletedMemberAndInvalidIdAndUnknownStatus(){
		signedIn("missing@example.test");when(members.findByEmail(anyString())).thenReturn(Optional.empty());assertThrows(InsufficientAuthenticationException.class,provider::getCurrentUserId);
		Member member=mock(Member.class);when(members.findByEmail(anyString())).thenReturn(Optional.of(member));
		when(member.getStatus()).thenReturn(Member.MemberStatus.ACTIVE);when(member.getId()).thenReturn(0L);assertThrows(InsufficientAuthenticationException.class,provider::getCurrentUserId);
		when(member.getId()).thenReturn(1L);when(member.getStatus()).thenReturn(null);assertThrows(LockedException.class,provider::getCurrentUserId);
	}
	@Test void warnedMemberCanUseProjects(){
		Member member=mock(Member.class);when(member.getId()).thenReturn(1L);when(member.getStatus()).thenReturn(Member.MemberStatus.WARNED);
		when(members.findByEmail("member@devmozip.demo")).thenReturn(Optional.of(member));signedIn("member@devmozip.demo");assertEquals(1,provider.getCurrentUserId());
	}
}

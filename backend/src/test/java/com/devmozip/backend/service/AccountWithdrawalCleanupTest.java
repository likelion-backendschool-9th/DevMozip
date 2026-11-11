package com.devmozip.backend.service;

import java.util.List;
import com.devmozip.backend.client.PortOneClient;
import com.devmozip.backend.repository.AccountWithdrawalRepository;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.payment.BillingKeyCipher;
import com.devmozip.backend.storage.LocalProfileImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AccountWithdrawalCleanupTest {
	AccountWithdrawalTasks tasks = mock(AccountWithdrawalTasks.class);
	AccountWithdrawalRepository accounts = mock(AccountWithdrawalRepository.class);
	MemberRepository members = mock(MemberRepository.class);
	PortOneClient portOne = mock(PortOneClient.class);
	BillingKeyCipher cipher = mock(BillingKeyCipher.class);
	LocalProfileImageStorage images = mock(LocalProfileImageStorage.class);
	AccountWithdrawalCleanup cleanup = new AccountWithdrawalCleanup(tasks, accounts, members, portOne, cipher, images);
	String key = "withdrawal:cleanup:1";
	@BeforeEach void setup() {
		when(tasks.acquire(key)).thenReturn("lease");
		when(accounts.isWithdrawn(1)).thenReturn(true);
	}

	@Test void keepsFailedBillingKeyAndRetriesWithoutKeepingSuccessfulFiles() throws Exception {
		var job = new AccountWithdrawalTasks.Job(1, "encrypted", List.of("photo.png"));
		when(tasks.read(key)).thenReturn(job);
		when(cipher.decrypt("encrypted")).thenReturn("billing-key");
		when(images.url("photo.png")).thenReturn("https://test/photo.png");
		doThrow(new IllegalStateException("provider unavailable")).when(portOne).deleteBillingKey("billing-key");
		cleanup.process(key);
		var pending = ArgumentCaptor.forClass(AccountWithdrawalTasks.Job.class);
		verify(tasks).save(pending.capture());
		assertThat(pending.getValue().encryptedBillingKey()).isEqualTo("encrypted");
		assertThat(pending.getValue().imageFiles()).isEmpty();
		assertThat(pending.getValue().operationId()).isEqualTo(job.operationId());
		verify(images).delete("photo.png");
		verify(tasks, never()).removePrepared(any());
		verify(tasks).release(key, "lease");
	}

	@Test void fileFailureStaysQueuedAndSharedFilesArePreserved() throws Exception {
		when(tasks.read(key)).thenReturn(new AccountWithdrawalTasks.Job(1, null, List.of("failed.png", "shared.png")));
		when(images.url("failed.png")).thenReturn("failed-url");
		when(images.url("shared.png")).thenReturn("shared-url");
		when(members.existsByProfileImageOrBannerImage("shared-url", "shared-url")).thenReturn(true);
		doThrow(new java.io.IOException()).when(images).delete("failed.png");
		cleanup.process(key);
		var pending = ArgumentCaptor.forClass(AccountWithdrawalTasks.Job.class);
		verify(tasks).save(pending.capture());
		assertThat(pending.getValue().imageFiles()).containsExactly("failed.png");
		verify(images, never()).delete("shared.png");
	}

	@Test void previousFormatFileNamesAreDroppedInsteadOfRetriedForever() throws Exception {
		var job = new AccountWithdrawalTasks.Job(1, null, List.of("00000000-0000-0000-0000-000000000000.png"));
		when(tasks.read(key)).thenReturn(job);
		when(images.url("00000000-0000-0000-0000-000000000000.png"))
			.thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
		cleanup.process(key);
		verify(tasks).removePrepared(job);
		verify(tasks, never()).save(any());
		verify(images, never()).delete(any());
	}

	@Test void onlyProcessesCommittedWithdrawalsAndRemovesCompletedJob() {
		var job = new AccountWithdrawalTasks.Job(1, null, List.of());
		when(tasks.read(key)).thenReturn(job);
		when(accounts.isWithdrawn(1)).thenReturn(false);
		cleanup.process(key);
		verify(tasks).removePrepared(job);
		verifyNoInteractions(portOne, images);
		when(accounts.isWithdrawn(1)).thenReturn(true);
		cleanup.process(key);
		verify(tasks, times(2)).removePrepared(job);
	}

	@Test void doesNotProcessAReplacedPreparation() {
		var old = new AccountWithdrawalTasks.Job(1, "old-key", List.of());
		var replacement = new AccountWithdrawalTasks.Job(1, "new-key", List.of());
		when(tasks.read(key)).thenReturn(old, replacement);
		cleanup.process(key);
		verifyNoInteractions(portOne, images, cipher);
		verify(tasks, never()).removePrepared(any());
	}
}

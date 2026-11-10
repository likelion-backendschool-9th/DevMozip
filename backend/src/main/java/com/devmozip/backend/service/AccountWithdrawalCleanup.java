package com.devmozip.backend.service;

import java.util.ArrayList;
import com.devmozip.backend.client.PortOneClient;
import com.devmozip.backend.repository.AccountWithdrawalRepository;
import com.devmozip.backend.repository.MemberRepository;
import com.devmozip.backend.security.payment.BillingKeyCipher;
import com.devmozip.backend.storage.LocalProfileImageStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AccountWithdrawalCleanup {
	private static final Logger log = LoggerFactory.getLogger(AccountWithdrawalCleanup.class);
	private final AccountWithdrawalTasks tasks;
	private final AccountWithdrawalRepository accounts;
	private final MemberRepository members;
	private final PortOneClient portOne;
	private final BillingKeyCipher cipher;
	private final LocalProfileImageStorage images;

	public AccountWithdrawalCleanup(AccountWithdrawalTasks tasks, AccountWithdrawalRepository accounts, MemberRepository members,
		PortOneClient portOne, BillingKeyCipher cipher, LocalProfileImageStorage images) {
		this.tasks = tasks; this.accounts = accounts; this.members = members;
		this.portOne = portOne; this.cipher = cipher; this.images = images;
	}

	@Scheduled(fixedDelayString = "${account.withdrawal.cleanup-delay-ms:60000}")
	public void retryPending() {
		try {
			for (String key : tasks.keys()) process(key);
		} catch (RuntimeException e) {
			log.warn("탈퇴 정리 작업 저장소에 접근하지 못했습니다. 다음 주기에 재시도합니다.");
		}
	}

	public void process(String key) {
		String lease = tasks.acquire(key);
		if (lease == null) return;
		try {
			var job = tasks.read(key);
			if (job == null) return;
			if (!accounts.isWithdrawn(job.userId())) { tasks.removePrepared(job); return; }
			var current = tasks.read(key);
			if (current == null || !current.operationId().equals(job.operationId())) return;
			String billingKey = job.encryptedBillingKey();
			if (billingKey != null) {
				try { portOne.deleteBillingKey(cipher.decrypt(billingKey)); billingKey = null; }
				catch (RuntimeException e) { log.warn("탈퇴 빌링키 삭제를 재시도합니다: userId={}", job.userId()); }
			}
			var pending = new ArrayList<String>();
			for (String filename : job.imageFiles()) {
				try {
					String url = images.url(filename);
					if (!members.existsByProfileImageOrBannerImage(url, url)) images.delete(filename);
				} catch (org.springframework.web.server.ResponseStatusException e) {
					// 저장 형식이 바뀌기 전(/api/v1/profile-images/)의 파일 이름이다. 지금 저장소의 파일이 아니므로 다시 시도하지 않는다
					log.warn("이전 형식의 탈퇴 이미지 파일 이름은 정리하지 않습니다: userId={}", job.userId());
				} catch (java.io.IOException | RuntimeException e) { pending.add(filename); }
			}
			if (billingKey == null && pending.isEmpty()) tasks.removePrepared(job);
			else tasks.save(new AccountWithdrawalTasks.Job(job.userId(), billingKey, pending, job.operationId()));
		} catch (RuntimeException e) {
			log.warn("탈퇴 정리 작업을 완료하지 못했습니다. 다음 주기에 재시도합니다.");
		} finally { tasks.release(key, lease); }
	}
}

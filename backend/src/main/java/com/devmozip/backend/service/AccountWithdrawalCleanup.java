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
			if (job == null || !accounts.isWithdrawn(job.userId())) return;
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
				} catch (java.io.IOException | RuntimeException e) { pending.add(filename); }
			}
			if (billingKey == null && pending.isEmpty()) tasks.remove(job.userId());
			else tasks.save(new AccountWithdrawalTasks.Job(job.userId(), billingKey, pending));
		} catch (RuntimeException e) {
			log.warn("탈퇴 정리 작업을 완료하지 못했습니다. 다음 주기에 재시도합니다.");
		} finally { tasks.release(key, lease); }
	}
}

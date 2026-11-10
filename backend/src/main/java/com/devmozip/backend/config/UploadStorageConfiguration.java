package com.devmozip.backend.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 업로드 파일 이름의 시각(yyyyMMddHHmmss). 프로필·배너 이미지(IMAGE_STORAGE)와 포트폴리오 파일(FILE_STORAGE)이 쓴다.
 * 게시글 이미지와 같이 한국 시각으로 남긴다 (docs/profile-local-images.md, docs/portfolio-files-api.md).
 */
@Configuration(proxyBeanMethods = false)
public class UploadStorageConfiguration {
	@Bean("uploadClock")
	Clock uploadClock() {
		return Clock.system(ZoneId.of("Asia/Seoul"));
	}
}

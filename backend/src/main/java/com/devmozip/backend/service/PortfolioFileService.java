package com.devmozip.backend.service;

import com.devmozip.backend.dto.portfolio.PortfolioFileResponse;
import com.devmozip.backend.storage.LocalPortfolioFileStorage.Metadata;
import org.springframework.web.multipart.MultipartFile;

public interface PortfolioFileService {
    PortfolioFileResponse upload(String email, MultipartFile file);
    /** 권한을 확인하고 파일 내용을 읽는다. nginx 없이 실행할 때 쓴다. */
    FileContent read(String email, String id);
    /** 권한을 확인하고 폴더 안의 파일 이름만 돌려준다. nginx가 X-Accel-Redirect로 파일을 보낼 때 쓴다. */
    FileLocation locate(String email, String id);
    record FileContent(Metadata metadata, byte[] bytes) {}
    record FileLocation(Metadata metadata, String storedName) {}
}

package com.devmozip.backend.service;

import com.devmozip.backend.dto.portfolio.PortfolioFileResponse;
import com.devmozip.backend.storage.LocalPortfolioFileStorage.Metadata;
import org.springframework.web.multipart.MultipartFile;

public interface PortfolioFileService {
    PortfolioFileResponse upload(String email, MultipartFile file);
    FileContent read(String email, String id);
    record FileContent(Metadata metadata, byte[] bytes) {}
}

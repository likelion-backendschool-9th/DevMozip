package com.devmozip.backend.service;

import com.devmozip.backend.dto.portfolio.PortfolioDetailResponse;
import com.devmozip.backend.dto.portfolio.PortfolioWriteRequest;
import com.devmozip.backend.dto.portfolio.PortfolioVisibilityRequest;
public interface PortfolioMutationService {
    PortfolioDetailResponse create(String email,PortfolioWriteRequest input);
    PortfolioDetailResponse update(String email,long id,PortfolioWriteRequest input);
    PortfolioDetailResponse changeVisibility(String email,long id,PortfolioVisibilityRequest input);
    void delete(String email,long id);
}

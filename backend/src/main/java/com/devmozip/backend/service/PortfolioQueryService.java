package com.devmozip.backend.service;
import com.devmozip.backend.dto.portfolio.*;
public interface PortfolioQueryService {
    PortfolioPageResponse own(String email,int page,int size);
    PortfolioPageResponse publicList(String slug,int page,int size);
    PortfolioDetailResponse detail(String email,long id);
}

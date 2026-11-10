package com.devmozip.backend.dto.project;
import java.util.List;
public record ProjectReportPageResponse(List<ManagedProjectReportResponse> items,long totalElements,int page,int totalPages) {}

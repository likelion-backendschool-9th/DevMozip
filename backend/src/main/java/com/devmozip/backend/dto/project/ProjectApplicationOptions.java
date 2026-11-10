package com.devmozip.backend.dto.project;
import java.util.List;
import com.devmozip.backend.service.ProjectPortfolioAccess.Portfolio;
public record ProjectApplicationOptions(List<Role> roles,List<Portfolio> portfolios,boolean portfoliosAvailable,
	ProjectApplicationResponse application,boolean canApply,String unavailableReason) {
	public record Role(String roleCode,int capacity,long filled,int minimumYears) {}
}

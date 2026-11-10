package com.devmozip.backend.config;

import com.devmozip.backend.repository.*;
import com.devmozip.backend.service.*;
import com.devmozip.backend.service.impl.DatabaseProjectPortfolioAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false)
public class ProjectPortfolioAccessConfiguration {
	@Bean @ConditionalOnMissingBean(ProjectPortfolioAccess.class)
	ProjectPortfolioAccess projectPortfolioAccess(ProjectPortfolioRepository portfolios,ProjectApplicationPortfolioSnapshotRepository snapshots,PortfolioQueryService queries,MemberRepository users,ObjectMapper mapper){return new DatabaseProjectPortfolioAccess(portfolios,snapshots,queries,users,mapper);}
}

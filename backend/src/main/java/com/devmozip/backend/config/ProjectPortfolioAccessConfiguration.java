package com.devmozip.backend.config;

import com.devmozip.backend.repository.*;
import com.devmozip.backend.service.ProjectPortfolioAccess;
import com.devmozip.backend.service.impl.DatabaseProjectPortfolioAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
public class ProjectPortfolioAccessConfiguration {
	@Bean @ConditionalOnMissingBean(ProjectPortfolioAccess.class)
	ProjectPortfolioAccess projectPortfolioAccess(ProjectPortfolioRepository portfolios,PortfolioMutationRepository mutations){return new DatabaseProjectPortfolioAccess(portfolios,mutations);}
}

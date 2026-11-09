package com.devmozip.backend.config;
import java.util.List;
import com.devmozip.backend.service.ProjectPortfolioAccess;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
public class ProjectPortfolioAccessConfiguration {
	@Bean @ConditionalOnMissingBean(ProjectPortfolioAccess.class)
	ProjectPortfolioAccess unavailablePortfolioAccess(){return new ProjectPortfolioAccess(){
		public boolean available(){return false;}
		public List<Portfolio> ownedBy(long userId){return List.of();}
		public boolean ownsAll(long userId,List<Long> ids){return false;}
	};}
}

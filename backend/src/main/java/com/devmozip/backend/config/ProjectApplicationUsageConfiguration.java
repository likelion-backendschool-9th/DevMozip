package com.devmozip.backend.config;

import java.util.HashSet;
import com.devmozip.backend.service.ProjectApplicationUsage;
import com.devmozip.backend.entity.enums.ProjectJobRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class ProjectApplicationUsageConfiguration {
	@Bean
	@ConditionalOnMissingBean(ProjectApplicationUsage.class)
	ProjectApplicationUsage projectApplicationUsage(JdbcTemplate jdbc) {
		return id -> new HashSet<>(jdbc.query("SELECT DISTINCT job_role FROM project_applications WHERE project_id=? AND status IN ('PENDING','APPROVED')",
			(rs,row) -> ProjectJobRole.valueOf(rs.getString(1)),id));
	}
}

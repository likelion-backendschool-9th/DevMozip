package com.devmozip.backend.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration(proxyBeanMethods=false)
public class PortfolioMultipartConfiguration {
    @Bean
    MultipartConfigElement multipartConfigElement() {
        var factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(10));
        factory.setMaxRequestSize(DataSize.ofMegabytes(11));
        factory.setFileSizeThreshold(DataSize.ofKilobytes(256));
        return factory.createMultipartConfig();
    }
}

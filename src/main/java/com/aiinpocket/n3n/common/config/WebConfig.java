package com.aiinpocket.n3n.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * MVC 非同步（SSE / Flux 串流回應）的執行器：虛擬執行緒 per-task。
     * 未設定時 Spring 落回 SimpleAsyncTaskExecutor 並在日誌警告不適合生產環境。
     * 逾時設 10 分鐘以涵蓋 AI 生成流程等長串流。
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("n3n-mvc-async-");
        executor.setVirtualThreads(true);
        configurer.setTaskExecutor(executor);
        configurer.setDefaultTimeout(600_000L);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static files and forward unknown paths to index.html for SPA routing
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String resourcePath, Resource location) throws IOException {
                    Resource requestedResource = location.createRelative(resourcePath);

                    // If the resource exists and is readable, return it
                    if (requestedResource.exists() && requestedResource.isReadable()) {
                        return requestedResource;
                    }

                    // For API calls, return null to let controllers handle them
                    if (resourcePath.startsWith("api/") || resourcePath.startsWith("ws/")) {
                        return null;
                    }

                    // For SPA routing, return index.html
                    return new ClassPathResource("/static/index.html");
                }
            });
    }
}

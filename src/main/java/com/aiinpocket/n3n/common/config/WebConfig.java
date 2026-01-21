package com.aiinpocket.n3n.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

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

package com.example.hotel_service.debug;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@Configuration
public class MappingsLogger {

    @Bean
    public ApplicationRunner logMappings(RequestMappingHandlerMapping mapping) {
        return args -> mapping.getHandlerMethods().forEach((info, method) -> {
            System.out.println("MAPPING: " + info + " -> " + method);
        });
    }
}

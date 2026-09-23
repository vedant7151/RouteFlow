package com.routeflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RouteFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(RouteFlowApplication.class, args);
    }
}

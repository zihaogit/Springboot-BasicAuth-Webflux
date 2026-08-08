package com.example.springbootbasiclogin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.web.reactive.config.EnableWebFlux;

@SpringBootApplication
@EnableWebFlux
@EnableAspectJAutoProxy
public class SpringbootbasicloginApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringbootbasicloginApplication.class, args);
    }

}

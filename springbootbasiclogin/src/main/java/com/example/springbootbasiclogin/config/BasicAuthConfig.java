package com.example.springbootbasiclogin.config;

import com.example.springbootbasiclogin.annotation.Authenticated;
import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.exception.CustomException;
import com.example.springbootbasiclogin.helper.BasicAuthHelper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Configuration
@Slf4j
public class BasicAuthConfig {

    private final BasicAuthHelper basicAuthHelper;

    @Autowired
    public BasicAuthConfig(BasicAuthHelper basicAuthHelper) {
        this.basicAuthHelper = basicAuthHelper;
    }

    @Before("@annotation(com.example.springbootbasiclogin.annotation.Authenticated)")
    public void setBasicAuthHelper(JoinPoint joinPoint) {
        try {
            //Getting the ServerWebExchange
            ServerWebExchange exchange = (ServerWebExchange) joinPoint.getArgs()[0];

            /* --- Authentication Happen Here --- */
            //Check whether the people is Authenticated
            Mono<Boolean> isAuthenticated = basicAuthHelper.checkAuthentication(exchange);

            /* --- Authorization Happen Here --- */
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Authenticated authenticated = method.getAnnotation(Authenticated.class);
            //Get the roles passing in the Custom Annotation
            String[] requiredRoles = authenticated.roles();
            log.info("Required Role: {}", Arrays.toString(requiredRoles));

        } catch (Exception e) {
            throw new CustomException(AuthResponseCode.AUTH_000520_NOT_FOUND, e);
        }
    }
}

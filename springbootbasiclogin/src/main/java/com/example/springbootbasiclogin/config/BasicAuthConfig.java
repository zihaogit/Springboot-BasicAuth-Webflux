package com.example.springbootbasiclogin.config;

import com.example.springbootbasiclogin.annotation.Authenticated;
import com.example.springbootbasiclogin.helper.BasicAuthHelper;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.server.context.SecurityContextServerWebExchange;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Configuration
public class BasicAuthConfig {

    private final BasicAuthHelper basicAuthHelper;

    @Autowired
    public BasicAuthConfig(BasicAuthHelper basicAuthHelper) {
        this.basicAuthHelper = basicAuthHelper;
    }

    @Before("@annotation(com.example.springbootbasiclogin.annotation.Authenticated)")
    public void setBasicAuthHelper(JoinPoint joinPoint){
        try{
            ServerRequest serverRequest= (ServerRequest)
                    ((SecurityContextServerWebExchange) joinPoint.getArgs()[0])
                            .getAttributes()
                            .get("ORIGINAL_REQUEST");

            /* --- Authentication Happen Here --- */
            //Check whether the people is Authenticated
            Mono<Boolean> isAuthenticated= basicAuthHelper.checkAuthentication(serverRequest);

            /* --- Authorization Happen Here --- */
            MethodSignature signature= (MethodSignature) joinPoint.getSignature();
            Method method= signature.getMethod();
            Authenticated authenticated= method.getAnnotation(Authenticated.class);
            //Get the roles passing in the Custom Annotation
            String[] requiredRoles = authenticated.roles();
            System.out.println(Arrays.toString(requiredRoles));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

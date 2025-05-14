package com.example.springbootbasiclogin.annotation;

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

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Configuration
@Slf4j
public class AuthenticatedAspect {

    private final BasicAuthHelper basicAuthHelper;

    @Autowired
    public AuthenticatedAspect(BasicAuthHelper basicAuthHelper) {
        this.basicAuthHelper = basicAuthHelper;
    }

    @Before("@annotation(com.example.springbootbasiclogin.annotation.Authenticated)")
    public void setBasicAuthHelper(JoinPoint joinPoint) {
        try {
            //Getting the ServerWebExchange
            ServerWebExchange exchange = (ServerWebExchange) joinPoint.getArgs()[0];

            /* --- Authentication Happen Here --- */
            //Check whether the people is Authenticated
            basicAuthHelper.checkAuthentication(exchange)
                    .subscribe(isAuthenticated -> {
                        if (Boolean.FALSE.equals(isAuthenticated)) {
                            throw new CustomException(AuthResponseCode.AUTH_000401_UNAUTHORIZED);
                        }
                    }, error -> {
                        throw new CustomException(AuthResponseCode.AUTH_000104_INVALID_AUTHENTICATION, error);
                    });

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

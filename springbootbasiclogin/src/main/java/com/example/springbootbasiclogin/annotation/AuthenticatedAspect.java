package com.example.springbootbasiclogin.annotation;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Configuration
@Slf4j
public class AuthenticatedAspect {

    @Around("@annotation(com.example.springbootbasiclogin.annotation.Authenticated)")
    public Object checkSecurityContext(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Authenticated authenticated = method.getAnnotation(Authenticated.class);
        String[] requiredRoles = authenticated.roles();

        Class<?> returnType = signature.getReturnType();
        boolean isFlux = Flux.class.isAssignableFrom(returnType);

        Mono<Object> resultMono = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.isAuthenticated())
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000401_UNAUTHORIZED)))
                .flatMap(auth -> {
                    if (requiredRoles.length > 0) {
                        Set<String> userRoles = auth.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .map(r -> r.startsWith("ROLE_") ? r.substring(5).toUpperCase() : r.toUpperCase())
                                .collect(Collectors.toSet());

                        boolean hasRole = Arrays.stream(requiredRoles)
                                .map(String::toUpperCase)
                                .anyMatch(userRoles::contains);

                        if (!hasRole) {
                            log.warn("Access denied for user {}. Required roles: {}, user roles: {}",
                                    auth.getName(), Arrays.toString(requiredRoles), userRoles);
                            return Mono.error(new CustomException(AuthResponseCode.AUTH_000403_ACCESS_DENIED));
                        }
                    }

                    try {
                        Object proceedResult = joinPoint.proceed();
                        if (proceedResult instanceof Mono) {
                            return (Mono<?>) proceedResult;
                        } else if (proceedResult instanceof Flux) {
                            return ((Flux<?>) proceedResult).collectList();
                        } else {
                            return Mono.justOrEmpty(proceedResult);
                        }
                    } catch (Throwable e) {
                        return Mono.error(new CustomException(AuthResponseCode.AUTH_000500_SERVER_ERROR, e));
                    }
                });

        if (isFlux) {
            return resultMono.flatMapMany(obj -> {
                if (obj instanceof List) {
                    return Flux.fromIterable((List<?>) obj);
                }
                return Flux.just(obj);
            });
        }
        return resultMono;
    }
}

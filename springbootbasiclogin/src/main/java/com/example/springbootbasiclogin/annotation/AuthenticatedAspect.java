package com.example.springbootbasiclogin.annotation;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Aspect
@Component
@Slf4j
public class AuthenticatedAspect {

    public static final String ROLE_PREFIX = "ROLE_";

    @Around("@annotation(com.example.springbootbasiclogin.annotation.Authenticated)")
    public Object checkSecurityContext(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Authenticated authenticated = method.getAnnotation(Authenticated.class);
        String[] requiredRoles = authenticated.roles();

        Class<?> returnType = signature.getReturnType();
        boolean isFlux = Flux.class.isAssignableFrom(returnType);

        if (isFlux) {
            return ReactiveSecurityContextHolder.getContext()
                    .map(SecurityContext::getAuthentication)
                    .filter(auth -> auth != null && auth.isAuthenticated())
                    .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000401_UNAUTHORIZED)))
                    .flatMapMany(auth -> validateRoles(auth, requiredRoles)
                            .thenMany(handleFluxProceed(joinPoint)));
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.isAuthenticated())
                .switchIfEmpty(Mono.error(new CustomException(AuthResponseCode.AUTH_000401_UNAUTHORIZED)))
                .flatMap(auth -> validateRoles(auth, requiredRoles)
                        .then(handleMonoProceed(joinPoint)));
    }

    private Flux<Object> handleFluxProceed(ProceedingJoinPoint joinPoint) {
        return Flux.defer(() -> {
            try {
                Object proceedResult = joinPoint.proceed();
                if (proceedResult instanceof Flux<?> flux) {
                    return flux.cast(Object.class);
                } else if (proceedResult instanceof Mono<?> mono) {
                    return mono.flux().cast(Object.class);
                } else if (proceedResult != null) {
                    return Flux.just(proceedResult);
                } else {
                    return Flux.empty();
                }
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                return Flux.error(e instanceof CustomException ce ? ce : new CustomException(AuthResponseCode.AUTH_000500_SERVER_ERROR, e));
            }
        });
    }

    private Mono<?> handleMonoProceed(ProceedingJoinPoint joinPoint) {
        return Mono.defer(() -> {
            try {
                Object proceedResult = joinPoint.proceed();
                if (proceedResult instanceof Mono<?> mono) {
                    return mono;
                } else {
                    return Mono.justOrEmpty(proceedResult);
                }
            } catch (Error e) {
                throw e;
            } catch (Throwable e) {
                return Mono.error(e instanceof CustomException ce ? ce : new CustomException(AuthResponseCode.AUTH_000500_SERVER_ERROR, e));
            }
        });
    }

    private Mono<Void> validateRoles(Authentication auth, String[] requiredRoles) {
        if (requiredRoles == null || requiredRoles.length == 0) {
            return Mono.empty();
        }

        Set<String> userRoles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.startsWith(ROLE_PREFIX) ? r.substring(ROLE_PREFIX.length()).toUpperCase() : r.toUpperCase())
                .collect(Collectors.toSet());

        boolean hasRole = Arrays.stream(requiredRoles)
                .map(String::toUpperCase)
                .anyMatch(userRoles::contains);

        if (!hasRole) {
            log.warn("Access denied for user {}. Required roles: {}, user roles: {}",
                    auth.getName(), Arrays.toString(requiredRoles), userRoles);
            return Mono.error(new CustomException(AuthResponseCode.AUTH_000403_ACCESS_DENIED));
        }

        return Mono.empty();
    }
}

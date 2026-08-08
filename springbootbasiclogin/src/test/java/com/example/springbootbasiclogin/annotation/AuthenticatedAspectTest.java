package com.example.springbootbasiclogin.annotation;

import com.example.springbootbasiclogin.constant.AuthResponseCode;
import com.example.springbootbasiclogin.exception.CustomException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticatedAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @InjectMocks
    private AuthenticatedAspect aspect;

    // Helper interface for reflective test target methods
    private interface DummyTarget {
        @Authenticated(roles = {"ADMIN"})
        Mono<String> adminOnlyMono();

        @Authenticated(roles = {"USER", "ADMIN"})
        Mono<String> userOrAdminMono();

        @Authenticated(roles = {"ADMIN"})
        Flux<String> adminOnlyFlux();
    }

    @Test
    @DisplayName("Aspect allows request when user has required role")
    void testAspectAuthorized() throws Throwable {
        Method method = DummyTarget.class.getMethod("adminOnlyMono");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        doReturn(method).when(methodSignature).getMethod();
        doReturn(Mono.class).when(methodSignature).getReturnType();
        when(joinPoint.proceed()).thenReturn(Mono.just("Success"));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        Object result = aspect.checkSecurityContext(joinPoint);

        @SuppressWarnings("unchecked")
        Mono<String> monoResult = (Mono<String>) result;

        StepVerifier.create(monoResult.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .expectNext("Success")
                .verifyComplete();
    }

    @Test
    @DisplayName("Aspect denies request with 403 when user lacks required role")
    void testAspectForbidden() throws Throwable {
        Method method = DummyTarget.class.getMethod("adminOnlyMono");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        doReturn(method).when(methodSignature).getMethod();
        doReturn(Mono.class).when(methodSignature).getReturnType();

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        Object result = aspect.checkSecurityContext(joinPoint);

        @SuppressWarnings("unchecked")
        Mono<String> monoResult = (Mono<String>) result;

        StepVerifier.create(monoResult.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .expectErrorMatches(t -> t instanceof CustomException &&
                        ((CustomException) t).getAuthResponseCode() == AuthResponseCode.AUTH_000403_ACCESS_DENIED)
                .verify();
    }

    @Test
    @DisplayName("Aspect denies request with 401 when no authentication present")
    void testAspectUnauthorized() throws Throwable {
        Method method = DummyTarget.class.getMethod("adminOnlyMono");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        doReturn(method).when(methodSignature).getMethod();
        doReturn(Mono.class).when(methodSignature).getReturnType();

        Object result = aspect.checkSecurityContext(joinPoint);

        @SuppressWarnings("unchecked")
        Mono<String> monoResult = (Mono<String>) result;

        StepVerifier.create(monoResult)
                .expectErrorMatches(t -> t instanceof CustomException &&
                        ((CustomException) t).getAuthResponseCode() == AuthResponseCode.AUTH_000401_UNAUTHORIZED)
                .verify();
    }

    @Test
    @DisplayName("Aspect supports Flux return types for authorized user")
    void testAspectFluxSupport() throws Throwable {
        Method method = DummyTarget.class.getMethod("adminOnlyFlux");
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        doReturn(method).when(methodSignature).getMethod();
        doReturn(Flux.class).when(methodSignature).getReturnType();
        when(joinPoint.proceed()).thenReturn(Flux.just("Item 1", "Item 2"));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        Object result = aspect.checkSecurityContext(joinPoint);

        @SuppressWarnings("unchecked")
        Flux<String> fluxResult = (Flux<String>) result;

        StepVerifier.create(fluxResult.contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .expectNext("Item 1", "Item 2")
                .verifyComplete();
    }
}

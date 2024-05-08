package com.example.springbootbasiclogin.helper;

import com.example.springbootbasiclogin.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class BasicAuthHelper {

    final private AuthService authService;

    @Autowired
    public BasicAuthHelper(AuthService authService) {
        this.authService = authService;
    }

    public Mono<Boolean> checkAuthentication (ServerRequest request){
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(securityContext -> {
                    Authentication authentication = securityContext.getAuthentication();
                    if(authentication != null){
                        //Access headers through exchange object
                        ServerWebExchange exchange= request.exchange();
                        HttpHeaders headers= exchange.getRequest().getHeaders();
                        String authorizationHeader = headers.getFirst("Authorization");

                        if(authorizationHeader == null || !authorizationHeader.startsWith("Basic")){
                            System.out.println("Invalid Authorization Header (missing or not Basic)");
                            return Mono.just(false);
                        }

                        //Remove "Basic" Prefix and decode the authorization header
                        String base64Credentials =  authorizationHeader.substring("Basic ".length());
                        String credentials = new String(Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8);

                        //Split username and password
                        String[] parts = credentials.split(":",2);
                        if(parts.length == 2){
                            String username = parts[0];
                            String password = parts[1];

                            //check valid password or username here
                            return authService.loginUser(username,password);
                        }
                    }
                    else {
                        System.out.println("No authentication information found");
                    }
                    return Mono.just(false);
                });
    }
}

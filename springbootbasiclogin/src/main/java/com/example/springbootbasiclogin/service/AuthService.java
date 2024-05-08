package com.example.springbootbasiclogin.service;

import com.example.springbootbasiclogin.entity.Roles;
import com.example.springbootbasiclogin.entity.Users;
import com.example.springbootbasiclogin.entity.VerificationToken;
import com.example.springbootbasiclogin.repo.RoleRepository;
import com.example.springbootbasiclogin.repo.UserRepository;
import com.example.springbootbasiclogin.repo.VerificationTokenRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService implements ReactiveUserDetailsService{

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Autowired
    public AuthService(UserRepository userRepository, RoleRepository roleRepository, VerificationTokenRepository verificationTokenRepository, BCryptPasswordEncoder passwordEncoder, JavaMailSender mailSender) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    //find the User + Roles using the username when require by basic auth
    @Override
    public Mono<UserDetails> findByUsername(String username) {
        return userRepository
                .findByUsername(username)
                .flatMap(user ->
                    roleRepository
                        .findByUserId(user.getId())
                        .map(Roles::getRole)
                        .collectList()
                        .map(roles -> User
                                .withUsername(user.getUsername())
                                .password(user.getPassword())
                                .roles(roles.toArray(new String[0]))
                                .build()
                        )
                );
    }

    public Mono<Boolean> loginUser(String username, String password) {
        return userRepository.findByUsername(username)
            .filter(user -> {
                //Match the password without {bcrypt} at front
                return passwordEncoder.matches(password, user.getPassword().substring(8));
            })
            .flatMap(user -> {
                //updated "active" status to true
                user.setActive(true);
                return userRepository.save(user)
                        .thenReturn(true);
            })
            .defaultIfEmpty(false);
    }

    public Mono<Users> registerUser(String username, String password, String role, String email) {
        //create new user
        Users user= new Users();
        user.setUsername(username);
        user.setPassword("{bcrypt}"+ passwordEncoder.encode(password));
        user.setEmail(email);

        return userRepository.save(user) //save the user to db
                .flatMap(savedUser -> {
                    //create new role
                    Roles roles = new Roles();
                    roles.setUserId(savedUser.getId());
                    roles.setRole(role);
                    //create new verificationToken
                    Mono<VerificationToken> savedToken = generateToken(user);

                    return roleRepository.save(roles)  //save the role to db
                            .then(savedToken
                                    .doOnSuccess(tokenSaved -> {
                                        String subject = "Email Verification";
                                        String content = "Please click the link below to verify your registration:<br>"
                                                + "<h3><a href=\"[[URL]]\">VERIFY</a></h3>"
                                                + "Thank you,<br>"
                                                + "Your company name.";

                                        String verifyURL =  "http://localhost:8080/verify-email/" + tokenSaved.getToken();
                                        content = content.replace("[[URL]]", verifyURL);

                                        sendEmail(savedUser.getEmail(), subject, content);
                                    })
                                    .thenReturn(savedUser)
                            );
                });
    }

    public Mono<String> verifyEmail(String verificationToken) {
        return verificationTokenRepository.findByToken(verificationToken)
                .flatMap(token -> {
                    if (isTokenExpired(token.getCreationTime())) {
                        return Mono.just("Verification token has expired.");
                    }

                    return userRepository.findById(token.getUserId())
                            .flatMap(user -> {
                                user.setVerified(true); // Set the verified flag to true
                                return userRepository.save(user);
                            })
                            .thenReturn("Email verified successfully.");
                });
    }

    public Mono<String> forgetPassword(String email){
        return userRepository.findByEmail(email)
                .flatMap(user ->{
                    Mono<VerificationToken> savedToken = generateToken(user);
                    return savedToken
                            .doOnSuccess(tokenSaved ->{
                                String subject = "Reset Password";
                                String content = "Forget Password? Please click the link below to to change your password:<br>"
                                        + "<h3>http://localhost:8080/reset-password/" + tokenSaved.getToken()+ "</h3>"
                                        + "Bye Bye, Regards from:<br>"
                                        + "Your company name.";

                                sendEmail(user.getEmail(), subject, content);
                            })
                            .thenReturn("Do check your email for resetting password")
                            .defaultIfEmpty("Error! cannot save Verification Token");
                })
                .defaultIfEmpty("Email is not registered");
    }

    public Mono<String> resetPassword(String verificationToken, String password) {
        return verificationTokenRepository.findByToken(verificationToken)
                .flatMap(token -> {
                    if (isTokenExpired(token.getCreationTime())) {
                        return Mono.just("Verification token has expired.");
                    }

                    return userRepository.findById(token.getUserId())
                            .flatMap(user -> {
                                // Updated the password
                                user.setPassword("{bcrypt}"+ passwordEncoder.encode(password));
                                return userRepository.save(user);
                            })
                            .thenReturn("Password Reset successfully.");
                });
    }

    public Mono<String> logout(String username) {
        return userRepository.findByUsername(username)
                .flatMap(user -> {
                    user.setActive(false);
                    return userRepository.save(user)
                            .doOnSuccess(success -> SecurityContextHolder.clearContext())
                            .thenReturn("Logout successful");
                })
                .defaultIfEmpty("No User found with username: "+ username);
    }

    private Mono<VerificationToken> generateToken(Users savedUser) {
        return verificationTokenRepository.findByUserId(savedUser.getId())
                .flatMap(existingToken -> {
                    existingToken.setToken(UUID.randomUUID().toString());
                    existingToken.setCreationTime(LocalDateTime.now());
                    return verificationTokenRepository.save(existingToken);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    VerificationToken verificationToken = new VerificationToken();
                    verificationToken.setToken(UUID.randomUUID().toString());
                    verificationToken.setUserId(savedUser.getId());
                    verificationToken.setCreationTime(LocalDateTime.now());
                    return verificationTokenRepository.save(verificationToken);
                }));
    }

    private void sendEmail(String to,String subject, String text) {
        String fromAddress = "noreply@yourdomain.com";
        String senderName = "Your Company";

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message);

        try {
            helper.setFrom(fromAddress, senderName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, true);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        mailSender.send(message);
    }

    private boolean isTokenExpired(LocalDateTime creationTime) {
        LocalDateTime now = LocalDateTime.now();
        Duration duration = Duration.between(creationTime, now);
        return duration.toMinutes() > 5; // Token expires after 5 minutes
    }

    //Delete the Roles + VerificationToken + User using id
    public Mono<String> deleteUsersCascade(int userId){
        return roleRepository.deleteByUserId(userId)
                .then(verificationTokenRepository.deleteByUserId(userId))
                .then(userRepository.deleteById(userId)
                        .thenReturn("Deleted user id - " + userId)
                        .defaultIfEmpty("Error Deleted cause by Cascade")
                );
    }
}
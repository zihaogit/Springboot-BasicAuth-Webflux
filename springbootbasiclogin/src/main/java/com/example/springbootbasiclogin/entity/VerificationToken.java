package com.example.springbootbasiclogin.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table("verification_token")
public class VerificationToken {

    @Id
    @Column("token_id")
    private int id;

    @Column("user_id")
    private int userId; // Reference to the User entity

    @Column("token")
    private String token;

    @Column("creation_time")
    private LocalDateTime creationTime;
}
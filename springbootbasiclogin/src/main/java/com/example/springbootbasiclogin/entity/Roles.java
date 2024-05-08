package com.example.springbootbasiclogin.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table(name="roles")
public class Roles {

    @Id
    @Column("role_id")
    private int roleId; //1 , 2, 3

    @Column("user_id")
    private int userId; // 1 , 1 , 1

    @Column("role")
    private String role; //ADMIN, MODERATOR, USER
}
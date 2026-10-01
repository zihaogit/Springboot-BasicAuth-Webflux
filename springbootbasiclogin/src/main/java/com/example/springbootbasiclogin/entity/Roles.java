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
    private int roleId;

    @Column("user_id")
    private int userId;

    @Column("role")
    private String role;
}
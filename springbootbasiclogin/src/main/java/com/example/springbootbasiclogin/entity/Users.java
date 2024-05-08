package com.example.springbootbasiclogin.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@AllArgsConstructor
@Getter @Setter
@Table(name="users")
public class Users {

    // define fields
    @Id
    @Column("id")
    private int id;

    @Column("username")
    private String username;

    @Column("password")
    private String password;

    @Column("first_name")
    private String firstName;

    @Column("last_name")
    private String lastName;

    @Column("email")
    private String email;

    @Column("about")
    private String about;

    @Column("job_title")
    private String jobTitle;

    @Column("languages")
    private String languages;

    @Column("skills")
    private String skills;

    @Column("projects_experiences")
    private String projectsAndExperiences;

    @Column("assignments")
    private String assignments;

    @Column("profile_pic")
    private String profilePic;

    @Column("active")
    private boolean active;

    @Column("verified")
    private boolean verified;
}
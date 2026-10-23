package com.example.springbootbasiclogin.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@EqualsAndHashCode(of = "id")
@Table(name="users")
public class Users {

    // define fields
    @Id
    @Column("id")
    private int id;

    @Column("username")
    private String username;

    @JsonIgnore
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

    @JsonIgnore
    @Column("fusionauth_user_id")
    private String fusionAuthUserId;

    @CreatedDate
    @Column("created_at")
    private java.time.ZonedDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private java.time.ZonedDateTime updatedAt;
}
package com.example.springbootbasiclogin.dao.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileUpdateRequest {

    private String firstName;
    private String lastName;
    private String about;
    private String jobTitle;
    private String languages;
    private String skills;
    private String projectsAndExperiences;
    private String assignments;
    private String profilePic;
}

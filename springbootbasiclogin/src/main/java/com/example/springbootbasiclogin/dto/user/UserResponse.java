package com.example.springbootbasiclogin.dto.user;

import com.example.springbootbasiclogin.entity.Users;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private int id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String about;
    private String jobTitle;
    private String languages;
    private String skills;
    private String projectsAndExperiences;
    private String assignments;
    private String profilePic;
    private boolean active;
    private boolean verified;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    public static UserResponse from(Users user) {
        if (user == null) {
            return null;
        }
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .about(user.getAbout())
                .jobTitle(user.getJobTitle())
                .languages(user.getLanguages())
                .skills(user.getSkills())
                .projectsAndExperiences(user.getProjectsAndExperiences())
                .assignments(user.getAssignments())
                .profilePic(user.getProfilePic())
                .active(user.isActive())
                .verified(user.isVerified())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}

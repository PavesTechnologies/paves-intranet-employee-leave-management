package com.paves.employee_leave_management.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UserDTOFromUMS {
    @JsonProperty("user_uuid")
    private String userUuid;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String mail;
    private String contact;
    private String password;

    @JsonProperty("is_active")
    private boolean active;

    @JsonProperty("user_id")
    private int userId;

    private String gender;
}
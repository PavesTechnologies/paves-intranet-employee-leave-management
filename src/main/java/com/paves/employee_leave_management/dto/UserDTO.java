package com.paves.employee_leave_management.dto;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    private Long id;
    private String email;
    private String name; // NEW
    private List<String> roles;
    private List<String> permissions;

    // Getters and Setters
}

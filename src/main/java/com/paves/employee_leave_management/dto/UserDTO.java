package com.paves.employee_leave_management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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

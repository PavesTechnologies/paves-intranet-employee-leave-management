package com.paves.employee_leave_management.dto;


import lombok.Data;
import java.util.List;

// for mapping the user from the UMS systems
@Data
public class UserResponseDTO {

    private int total;
    private List<UserDTOFromUMS> users;
}

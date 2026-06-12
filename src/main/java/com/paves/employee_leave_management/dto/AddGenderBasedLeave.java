package com.paves.employee_leave_management.dto;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.paves.employee_leave_management.entities.GenderBasedLeave;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class AddGenderBasedLeave {
    private String updateType;
    private GenderBasedLeave genderBasedLeave;
}

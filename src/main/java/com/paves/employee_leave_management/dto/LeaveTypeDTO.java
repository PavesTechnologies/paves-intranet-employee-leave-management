package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.entities.LeaveType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LeaveTypeDTO {
    LeaveType leaveType;
    GenderBasedLeave genderBasedLeave;
}

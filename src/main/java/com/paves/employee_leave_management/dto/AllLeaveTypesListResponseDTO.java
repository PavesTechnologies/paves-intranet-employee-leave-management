package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.entities.GenderBasedLeave;
import com.paves.employee_leave_management.entities.LeaveType;
import lombok.*;

import java.util.List;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Data
public class AllLeaveTypesListResponseDTO {
    List<LeaveType> regular;
    List<GenderBasedLeave> genderBasedLeaves;
 }

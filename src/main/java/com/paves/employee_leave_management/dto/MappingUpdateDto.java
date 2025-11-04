package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.enums.BlockStatus;
import lombok.*;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Data
public class MappingUpdateDto {
    private String employeeId;
    private String leaveTypeId;
    private BlockStatus status; // ACTIVE / INACTIVE
}

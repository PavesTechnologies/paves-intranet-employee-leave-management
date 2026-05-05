package com.paves.employee_leave_management.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class LeaveWebSocketEvent {
            String type;      // WsEventType name
            String leaveId;
            long compOffId;
            String employeeId;
            String managerId;  // nullable

    public LeaveWebSocketEvent(String type, String leaveId, String employeeId, String managerId){
        this.type = type;
        this.leaveId = leaveId;
        this.employeeId = employeeId;
        this.managerId = managerId;
    }


    public LeaveWebSocketEvent(String type, String leaveId, String employeeId){
        this.type = type;
        this.leaveId = leaveId;
        this.employeeId = employeeId;
    }

    public LeaveWebSocketEvent(String type, long compOffId, String employeeId, String managerId){
        this.type = type;
        this.compOffId = compOffId;
        this.employeeId = employeeId;
        this.managerId = managerId;
    }



}


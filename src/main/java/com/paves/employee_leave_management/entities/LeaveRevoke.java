package com.paves.employee_leave_management.entities;

import com.paves.employee_leave_management.enums.LeaveRevokeStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LeaveRevoke {
    @Id
    private String id;

    @PrePersist
    private void generateId(){
        if(id == null){
            this.id = "LRV"+ UUID.randomUUID().toString().replace("-","").substring(0,5).toUpperCase();
        }
    }

    @Column(name = "leave_id")
    private String leaveRequestId;

    @Column(name ="reason")
    private String reason;

    @Column(name = "status")
    private LeaveRevokeStatus status;
}

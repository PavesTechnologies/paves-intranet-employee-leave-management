package com.paves.employee_leave_management.dto;

import com.paves.employee_leave_management.enums.ChangeImpact;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the changes made during a leave request update.
 * Used for impact assessment and notification purposes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveChangeDetails {
    
    private ChangeImpact impact;
    
    @Builder.Default
    private List<String> changes = new ArrayList<>();
    
    private boolean leaveTypeChanged;
    private boolean datesChanged;
    private boolean durationChanged;
    private boolean reasonChanged;
    private boolean documentationChanged;
    
    private int daysDifference;
    
    private String updatedBy; // employeeId of who made the update
    private String updateReason; // Optional reason for the update
    
    public void addChange(String change) {
        this.changes.add(change);
    }
    
    public boolean requiresWorkflowReset() {
        return impact == ChangeImpact.MAJOR;
    }
    
    public boolean requiresNotification() {
        return impact == ChangeImpact.MAJOR || impact == ChangeImpact.MINOR;
    }
}

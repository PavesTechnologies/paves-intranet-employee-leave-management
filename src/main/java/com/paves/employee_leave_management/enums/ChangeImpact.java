package com.paves.employee_leave_management.enums;

/**
 * Represents the severity of changes made to a leave request update.
 * Used to determine whether to preserve approval progress or restart workflow.
 */
public enum ChangeImpact {
    /**
     * Critical changes requiring complete workflow restart.
     * Examples: Leave type change, significant date/duration changes (>2 days)
     */
    MAJOR,
    
    /**
     * Moderate changes that may require re-notification but can preserve some approvals.
     * Examples: Date changes ≤2 days, reason updates, documentation links
     */
    MINOR,
    
    /**
     * Trivial changes that don't affect approval decisions.
     * Examples: Comment updates, formatting corrections
     */
    TRIVIAL
}

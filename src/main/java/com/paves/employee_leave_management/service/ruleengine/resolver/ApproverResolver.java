package com.paves.employee_leave_management.service.ruleengine.resolver;

import com.paves.employee_leave_management.entities.Request;
import com.paves.employee_leave_management.entities.ApprovalStep;
import com.paves.employee_leave_management.enums.ApproverType;

import java.util.List;
import java.util.UUID;

/**
 * Strategy interface for resolving approver IDs based on the ApprovalStep's type.
 */
public interface ApproverResolver {

    /**
     * Resolves the list of approver user IDs for a given step and request.
     *
     * @param request The main request object, containing maker details.
     * @param step    The ApprovalStep defining the logic (e.g., type and value).
     * @return A list of UUIDs for the resolved approvers. Can be empty.
     */
    List<String> resolve(Request request, ApprovalStep step);

    /**
     * The specific ApproverType this resolver handles.
     */
    ApproverType getApproverType();
}
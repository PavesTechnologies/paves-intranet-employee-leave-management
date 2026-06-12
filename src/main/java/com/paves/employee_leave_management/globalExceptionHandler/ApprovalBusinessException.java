package com.paves.employee_leave_management.globalExceptionHandler;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY) // 422
public class ApprovalBusinessException extends RuntimeException {

    private final String reason;

    public ApprovalBusinessException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}

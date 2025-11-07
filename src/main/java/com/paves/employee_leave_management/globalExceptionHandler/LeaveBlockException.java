package com.paves.employee_leave_management.globalExceptionHandler;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeaveBlockException extends RuntimeException {
    private final String exMsg;

    public LeaveBlockException(String exMsg) {
        super(exMsg);
        this.exMsg = exMsg;
    }

    public String getExMsg() {
        return exMsg;
    }
}

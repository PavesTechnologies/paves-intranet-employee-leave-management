package com.paves.employee_leave_management.globalExceptionHandler;


import com.paves.employee_leave_management.dto.RowError;
import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Getter
@Setter
public class UploadValidationException extends RuntimeException {
    private final List<RowError> errors;

    public UploadValidationException(List<RowError> errors) {
        super("Validation failed in one or more rows.");
        this.errors = errors;
    }

    public List<RowError> getErrors() {
        return errors;
    }
}
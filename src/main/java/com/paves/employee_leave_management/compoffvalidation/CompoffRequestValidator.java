package com.paves.employee_leave_management.compoffvalidation;

import com.paves.employee_leave_management.dto.LeaveCompoffRequestDTO;
import com.paves.employee_leave_management.entities.LeaveCompoff;
import com.paves.employee_leave_management.enums.LeaveStatusCompoff;
import com.paves.employee_leave_management.repo.LeaveCompoffRepo;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class CompoffRequestValidator implements ConstraintValidator<ValidCompoffRequest, LeaveCompoffRequestDTO> {

    private final LeaveCompoffRepo leaveCompoffRepo;
    public CompoffRequestValidator(LeaveCompoffRepo leaveCompoffRepo) {
        this.leaveCompoffRepo = leaveCompoffRepo;
    }

    @Override
    public boolean isValid(LeaveCompoffRequestDTO dto, ConstraintValidatorContext context) {
        if (dto == null) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();
        //note is notblank
        if (dto.getNote() == null || dto.getNote().trim().isEmpty()) {
            context.buildConstraintViolationWithTemplate("CompOff requires a comment while applying leave.")
                    .addPropertyNode("note")
                    .addConstraintViolation();
            valid = false;
        }

        //Ensure startDate is not more than 28 days in the past
        LocalDate today = LocalDate.now();
        if (dto.getStartDate() != null && dto.getStartDate().isBefore(today.minusDays(28))) {
            context.buildConstraintViolationWithTemplate("Compoff cannot be applied beyond 28 days in the past.")
                    .addPropertyNode("startDate")
                    .addConstraintViolation();
            valid = false;
        }

        //duplicate compoff check
        if (dto.getStartDate() != null && dto.getEndDate() != null && dto.getEmployeeId() != null) {
            List<LeaveCompoff> existingCompoffs = leaveCompoffRepo.findByEmployeeId(dto.getEmployeeId());

            for (LeaveCompoff existing : existingCompoffs) {
                if (existing.getStatus() == LeaveStatusCompoff.APPROVED ||
                        existing.getStatus() == LeaveStatusCompoff.PENDING) {

                    // Check for date overlap
                    if (!dto.getEndDate().isBefore(existing.getStartDate()) &&
                            !dto.getStartDate().isAfter(existing.getEndDate())) {

                        context.buildConstraintViolationWithTemplate(
                                        "Compoff already requested for the date(s) " +
                                                existing.getStartDate() + " to " + existing.getEndDate()
                                ).addPropertyNode("startDate")
                                .addConstraintViolation();
                        valid = false;
                        break;
                    }
                }
            }
        }

        return valid;
    }
}

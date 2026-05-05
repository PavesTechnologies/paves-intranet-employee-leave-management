package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.EmailDTO;
import com.paves.employee_leave_management.dto.LeaveCompoffRequestDTO;
import com.paves.employee_leave_management.dto.LeaveWebSocketEvent;
import com.paves.employee_leave_management.dto.PendingCompoffResponseDTO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveCompoff;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.enums.LeaveStatusCompoff;
import com.paves.employee_leave_management.enums.WsEventType;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveCompoffRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.AsyncNotificationServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveCompoffSerivceInterface;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;




@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveCompoffServiceImpl implements LeaveCompoffSerivceInterface {

    public enum CompoffEmailType {
        REQUEST,
        APPROVED,
        REJECTED,
        CANCELLED
    }

    private final LeaveCompoffRepo leaveCompoffRepo;
    private final LeaveBalanceRepo leaveBalanceRepo;
    private final EmployeeRepo employeeRepo;
    private final LeaveTypeRepo leaveTypeRepo;
    private final AsyncNotificationServiceInterface asyncNotificationService;





    @Override
    @Transactional
    public LeaveCompoff requestCompoff(LeaveCompoffRequestDTO dto) {

        Employee employee = employeeRepo.findById(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found: " + dto.getEmployeeId()));

        Employee manager = employee.getManager();

        if (manager == null) {
            throw new RuntimeException("No manager assigned for employee");
        }

        String managerId = manager.getEmployeeId();

        LeaveCompoff compoff = LeaveCompoff.builder()
                .employeeId(dto.getEmployeeId())
                .startDate(dto.getStartDate())
                .endDate(dto.getEndDate())
                .duration(dto.getDuration())
                .note(dto.getNote())
                .status(LeaveStatusCompoff.PENDING)
                .managerId(managerId)
                .startSession(dto.getStartSession())
                .endSession(dto.getEndSession())
                .build();

        LeaveCompoff savedCompoff = leaveCompoffRepo.save(compoff);

        // 🔥 Fire and forget email (non-blocking)
        sendCompoffEmail(CompoffEmailType.REQUEST, employee, manager, savedCompoff);

        return savedCompoff;
    }


    @Override
    public LeaveCompoff approveCompoff(Long compoffId) {

        LeaveCompoff compoff = leaveCompoffRepo.findById(compoffId)
                .orElseThrow(() -> new RuntimeException("Compoff request not found"));

        LeaveType leaveType = leaveTypeRepo.findByLeaveTypeId("L-COMPOFF").orElse(null);

        int expiryDays = 0;
        LocalDate expiryDate = null;

        if (leaveType != null && leaveType.getExpiryDays() != 0) {
            expiryDays = leaveType.getExpiryDays();
            expiryDate = LocalDate.now().plusDays(expiryDays);
        }

        LeaveStatusCompoff currentStatus = compoff.getStatus();

        if (currentStatus != LeaveStatusCompoff.PENDING &&
                currentStatus != LeaveStatusCompoff.REJECTED) {
            throw new RuntimeException("Only pending or rejected compoffs can be approved.");
        }

        LeaveBalance balance = leaveBalanceRepo
                .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                        compoff.getEmployeeId(),
                        "L-COMPOFF",
                        LocalDate.now().getYear()
                );

        if (balance == null) {
            throw new RuntimeException("Leave balance not found for employee: " + compoff.getEmployeeId());
        }

        double duration = compoff.getDuration();

        balance.setTotalLeaves(balance.getTotalLeaves() + duration);
        balance.setRemainingLeaves(balance.getRemainingLeaves() + duration);
        balance.setAccruedLeaves(balance.getAccruedLeaves() + duration);
        balance.setLastAccrualDate(LocalDate.now());

        compoff.setStatus(LeaveStatusCompoff.APPROVED);
        compoff.setActionDate(LocalDate.now());
        compoff.setExpiryDate(expiryDate);

        leaveCompoffRepo.save(compoff);
        leaveBalanceRepo.save(balance);

        // 🔥 Fire-and-forget email
        sendCompoffEmail(CompoffEmailType.APPROVED, compoff.getEmployee(), compoff.getEmployee().getManager(), compoff);

        return compoff;
    }


    @Override
    public LeaveCompoff rejectCompoff(Long compoffId) {

        LeaveCompoff compoff = leaveCompoffRepo.findById(compoffId)
                .orElseThrow(() -> new RuntimeException("Compoff request not found"));

        LeaveStatusCompoff currentStatus = compoff.getStatus();

        if (currentStatus != LeaveStatusCompoff.PENDING &&
                currentStatus != LeaveStatusCompoff.APPROVED) {
            throw new RuntimeException("Only pending or approved compoffs can be rejected.");
        }

        if (currentStatus == LeaveStatusCompoff.APPROVED) {
            LeaveBalance balance = leaveBalanceRepo
                    .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                            compoff.getEmployeeId(),
                            "L-COMPOFF",
                            LocalDate.now().getYear()
                    );

            if (balance == null) {
                throw new RuntimeException("Leave balance not found for employee: " + compoff.getEmployeeId());
            }

            double duration = compoff.getDuration();

            balance.setTotalLeaves(Math.max(0, balance.getTotalLeaves() - duration));
            balance.setRemainingLeaves(Math.max(0, balance.getRemainingLeaves() - duration));
            balance.setAccruedLeaves(Math.max(0, balance.getAccruedLeaves() - duration));

            leaveBalanceRepo.save(balance);
        }

        compoff.setStatus(LeaveStatusCompoff.REJECTED);
        compoff.setActionDate(LocalDate.now());
        compoff.setExpiryDate(null);


        // 🔥 Generic email call
        sendCompoffEmail(
                CompoffEmailType.REJECTED,
                compoff.getEmployee(),
                null,
                compoff
        );

        return leaveCompoffRepo.save(compoff);
    }

    @Override
    public List<LeaveCompoff> getCompoffsByEmployee(String employeeId) {
        return leaveCompoffRepo.findByEmployeeId(employeeId).stream().filter(e -> e.getStatus().equals(LeaveStatusCompoff.PENDING)).collect(Collectors.toList());
    }

    @Override
    public List<LeaveCompoff> getCompoffsByManagerAndStatus(String managerId, LeaveStatusCompoff status) {
        return leaveCompoffRepo.findByManagerIdAndStatus(managerId, status);
    }

    @Override
    public List<PendingCompoffResponseDTO> getPendingCompoffsForManager(String managerId) {
        List<LeaveCompoff> compoffs = leaveCompoffRepo.findByManagerIdAndStatus(managerId, LeaveStatusCompoff.PENDING);

        return compoffs.stream().map(compoff -> {
            Employee emp = compoff.getEmployee();
            if (emp == null) {
                throw new RuntimeException("Employee not found for compoff ID: " + compoff.getIdleaveCompoff());
            }

            PendingCompoffResponseDTO dto = new PendingCompoffResponseDTO();
            dto.setIdleaveCompoff(compoff.getIdleaveCompoff());
            dto.setEmployeeId(emp.getEmployeeId());
            dto.setEmployeeName(emp.getFirstName() + " " + emp.getLastName());
            dto.setStartDate(compoff.getStartDate());
            dto.setEndDate(compoff.getEndDate());
            dto.setDuration(compoff.getDuration());
            dto.setNote(compoff.getNote());
            dto.setStatus(compoff.getStatus().toString());
            dto.setActionDate(compoff.getActionDate());
            dto.setExpiryDate(compoff.getExpiryDate());
            dto.setStartSession(compoff.getStartSession());
            dto.setEndSession(compoff.getEndSession());

            return dto;
        }).collect(Collectors.toList());
    }


    @Override
    public void cancelPendingCompoff(Long compOffId) {
        LeaveCompoff compoff = leaveCompoffRepo.findById(compOffId)
                .orElseThrow(() -> new RuntimeException("CompOff request not found"));

        if (compoff.getStatus() != LeaveStatusCompoff.PENDING) {
            throw new RuntimeException("Only pending CompOff requests can be cancelled.");
        }

        compoff.setStatus(LeaveStatusCompoff.CANCELLED);
        compoff.setActionDate(LocalDate.now());

        // Optional: Save cancellation note
        compoff.setNote("cancelled by Employee");
        leaveCompoffRepo.save(compoff);
    }

    @Override
    public LeaveCompoff cancelPendingCompOffByEmployee(Long id) {
        LeaveCompoff compOff = leaveCompoffRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("CompOff request not Found "));

        compOff.setStatus(LeaveStatusCompoff.CANCELLED);
        compOff.setActionDate(LocalDate.now());


        compOff.setNote("Cancelled by employee: ");

        sendCompoffEmail(CompoffEmailType.CANCELLED, compOff.getEmployee(), null, compOff);

        return leaveCompoffRepo.save(compOff);
    }

    @Override
    public void expireUnusedCompoffs() {
        List<LeaveCompoff> compoffs = leaveCompoffRepo.findByStatus(LeaveStatusCompoff.APPROVED);

        for (LeaveCompoff compoff : compoffs) {
            if (compoff.getExpiryDate() != null &&
                    LocalDate.now().isAfter(compoff.getExpiryDate())) {

                compoff.setStatus(LeaveStatusCompoff.EXPIRED);
                leaveCompoffRepo.save(compoff);

                LeaveBalance balance = leaveBalanceRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(compoff.getEmployeeId(), "L-COMPOFF", LocalDate.now().getYear());

                if (balance != null) {
                    double days = compoff.getDuration();
                    balance.setTotalLeaves(balance.getTotalLeaves() - days);
                    balance.setRemainingLeaves(balance.getRemainingLeaves() - days);
                    balance.setAccruedLeaves(balance.getAccruedLeaves() - days);
                    leaveBalanceRepo.save(balance);
                }
            }
        }
    }

    private void sendCompoffEmail(
            CompoffEmailType type,
            Employee employee,
            Employee manager,
            LeaveCompoff compoff
    ) {
        try {

            String employeeFullName = employee.getFirstName() + " " + employee.getLastName();

            String subject = "";
            String recipientEmail = "";
            String recipientName = "";
            String messageBody = "";

            Map<String, String> details = new LinkedHashMap<>();

            switch (type) {

                case REQUEST:
                    subject = "New Comp-Off Request";
                    recipientEmail = manager.getEmail();
                    recipientName = manager.getFirstName();
                    messageBody = "A new comp-off request has been submitted by <strong>" + employeeFullName + "</strong>.";

                    details.put("Employee", employeeFullName);
                    details.put("Date", compoff.getStartDate().toString());
                    details.put("Reason", compoff.getNote());
                    break;

                case APPROVED:
                    subject = "Comp-Off Request Approved";
                    recipientEmail = employee.getEmail();
                    recipientName = employee.getFirstName();
                    messageBody = "Your comp-off request has been <strong>approved</strong>.";

                    details.put("Date", compoff.getStartDate().toString());
                    details.put("Reason", compoff.getNote());
                    break;

                case REJECTED:
                    subject = "Comp-Off Request Rejected";
                    recipientEmail = employee.getEmail();
                    recipientName = employee.getFirstName();
                    messageBody = "Your comp-off request has been <strong>rejected</strong>.";

                    details.put("Date", compoff.getStartDate().toString());
                    details.put("Reason", compoff.getNote());
                    break;

                case CANCELLED:
                    subject = "Comp-Off Request Cancelled";
                    recipientEmail = employee.getEmail();
                    recipientName = employee.getFirstName();
                    messageBody = "Your comp-off request has been <strong>Cancelled</strong>.";

                    details.put("Date", compoff.getStartDate().toString());
                    break;
            }

            Map<String, Object> templateModel = new LinkedHashMap<>();
            templateModel.put("title", subject);
            templateModel.put("recipientName", recipientName);
            templateModel.put("messageBody", messageBody);
            templateModel.put("detailsTitle", "Request Details");
            templateModel.put("details", details);
            templateModel.put("closingMessage", "Please check the Leave Management System.");

            EmailDTO emailDTO = new EmailDTO(
                    recipientEmail,
                    subject,
                    "generic-notification.html",
                    true
            );

            emailDTO.setTemplateModel(templateModel);

            asyncNotificationService.queueEmail(emailDTO);

        } catch (Exception e) {
            log.error("Failed to send comp-off email for type: {}", type, e);
        }
    }

}
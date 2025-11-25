package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.dto.LeaveCompoffRequestDTO;
import com.paves.employee_leave_management.dto.PendingCompoffResponseDTO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveCompoff;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.enums.LeaveStatusCompoff;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveCompoffRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.EmailServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveCompoffSerivceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveCompoffServiceImpl implements LeaveCompoffSerivceInterface {
    private final LeaveCompoffRepo leaveCompoffRepo;
    private final LeaveBalanceRepo leaveBalanceRepo;
    private final EmployeeRepo employeeRepo;
    private final LeaveTypeRepo leaveTypeRepo;
    private final EmailServiceInterface emailService;

    @Override
    public void requestCompoff(LeaveCompoffRequestDTO dto) {
        Employee employee = employeeRepo.findById(dto.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found: " + dto.getEmployeeId()));

        Employee manager = employee.getManager();

        if (manager == null) {
            throw new RuntimeException("No manager assigned for employee");
        }
        String managerId = employee.getManager().getEmployeeId();

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

        LeaveCompoff cf = leaveCompoffRepo.save(compoff);

        // Send notification to manager
        String employeeFullName = employee.getFirstName() + " " + employee.getLastName();
        Map<String, Object> templateModel = new LinkedHashMap<>();
        templateModel.put("title", "New Comp-Off Request");
        templateModel.put("recipientName", manager.getFirstName());
        templateModel.put("messageBody", "A new comp-off request has been submitted by <strong>" + employeeFullName + "</strong>.");
        templateModel.put("detailsTitle", "Request Details");

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Employee", employeeFullName);
        details.put("Date", cf.getStartDate().toString());
        details.put("Reason", cf.getNote());
        templateModel.put("details", details);

        templateModel.put("closingMessage", "Please review the request in the Leave Management System.");

        emailService.sendEmailFromTemplate(manager.getEmail(), "New Comp-Off Request", "generic-notification.html", templateModel);
    }


    @Override
    public void approveCompoff(Long compoffId) {
        LeaveCompoff compoff = leaveCompoffRepo.findById(compoffId)
                .orElseThrow(() -> new RuntimeException("Compoff request not found"));

        LeaveType leaveType = leaveTypeRepo.findByLeaveTypeId("L-COMPOFF").orElse(null);

        int expiryDays = 0;
        LocalDate date = null;

        if (leaveType != null && leaveType.getExpiryDays() != 0) {

            expiryDays = leaveType.getExpiryDays();
            date = LocalDate.now().plusDays(expiryDays);
        }


        LeaveStatusCompoff currentStatus = compoff.getStatus();

        // ✅ Allow approve if status is PENDING or REJECTED
        if (currentStatus != LeaveStatusCompoff.PENDING && currentStatus != LeaveStatusCompoff.REJECTED) {
            throw new RuntimeException("Only pending or rejected compoffs can be approved.");
        }

        LeaveBalance balance = leaveBalanceRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                compoff.getEmployeeId(), "L-COMPOFF", LocalDate.now().getYear());

        if (balance == null) {
            throw new RuntimeException("Leave balance not found for employee: " + compoff.getEmployeeId());
        }

        double duration = compoff.getDuration();
        balance.setTotalLeaves(balance.getTotalLeaves() + duration);
        balance.setRemainingLeaves(balance.getRemainingLeaves() + duration);
        balance.setAccruedLeaves(balance.getAccruedLeaves() + duration);

        compoff.setStatus(LeaveStatusCompoff.APPROVED);
        compoff.setActionDate(LocalDate.now());
        compoff.setExpiryDate(date);
        balance.setLastAccrualDate(LocalDate.now());

        leaveCompoffRepo.save(compoff);
        leaveBalanceRepo.save(balance);

        // Send notification to employee
        Employee employee = compoff.getEmployee();
        Map<String, Object> templateModel = new LinkedHashMap<>();
        templateModel.put("title", "Comp-Off Request Approved");
        templateModel.put("recipientName", employee.getFirstName());
        templateModel.put("messageBody", "Your comp-off request has been <strong>approved</strong>.");
        templateModel.put("detailsTitle", "Request Details");

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Date", compoff.getStartDate().toString());
        details.put("Reason", compoff.getNote());
        templateModel.put("details", details);

        emailService.sendEmailFromTemplate(employee.getEmail(), "Comp-Off Request Approved", "generic-notification.html", templateModel);
    }

    @Override
    public void rejectCompoff(Long compoffId) {
        LeaveCompoff compoff = leaveCompoffRepo.findById(compoffId)
                .orElseThrow(() -> new RuntimeException("Compoff request not found"));

        LeaveStatusCompoff currentStatus = compoff.getStatus();

        // ✅ Allow reject if status is PENDING or APPROVED
        if (currentStatus != LeaveStatusCompoff.PENDING && currentStatus != LeaveStatusCompoff.APPROVED) {
            throw new RuntimeException("Only pending or approved compoffs can be rejected.");
        }

        // ✅ If APPROVED → REJECTED, subtract from balance
        if (currentStatus == LeaveStatusCompoff.APPROVED) {
            LeaveBalance balance = leaveBalanceRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                    compoff.getEmployeeId(), "L-COMPOFF", LocalDate.now().getYear());

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
        compoff.setExpiryDate(null); // Optional

        leaveCompoffRepo.save(compoff);

        // Send notification to employee
        Employee employee = compoff.getEmployee();
        Map<String, Object> templateModel = new LinkedHashMap<>();
        templateModel.put("title", "Comp-Off Request Rejected");
        templateModel.put("recipientName", employee.getFirstName());
        templateModel.put("messageBody", "Your comp-off request has been <strong>rejected</strong>.");
        templateModel.put("detailsTitle", "Request Details");

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Date", compoff.getStartDate().toString());
        details.put("Reason", compoff.getNote());
        templateModel.put("details", details);

        emailService.sendEmailFromTemplate(employee.getEmail(), "Comp-Off Request Rejected", "generic-notification.html", templateModel);
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
    public void cancelPendingCompOffByEmployee(Long id) {
        LeaveCompoff compOff = leaveCompoffRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("CompOff request not Found "));

        compOff.setStatus(LeaveStatusCompoff.CANCELLED);
        compOff.setActionDate(LocalDate.now());


        compOff.setNote("Cancelled by employee: ");


        leaveCompoffRepo.save(compOff);

        // Send notification to employee
        Employee employee = compOff.getEmployee();
        Map<String, Object> templateModel = new LinkedHashMap<>();
        templateModel.put("title", "Comp-Off Request Cancelled");
        templateModel.put("recipientName", employee.getFirstName());
        templateModel.put("messageBody", "Your comp-off request has been <strong>cancelled</strong>.");
        templateModel.put("detailsTitle", "Request Details");

        Map<String, String> details = new LinkedHashMap<>();
        details.put("Date", compOff.getStartDate().toString());
        details.put("Reason", compOff.getNote());
        templateModel.put("details", details);

        emailService.sendEmailFromTemplate(employee.getEmail(), "Comp-Off Request Cancelled", "generic-notification.html", templateModel);

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

}
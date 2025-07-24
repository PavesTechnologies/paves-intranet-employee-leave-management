// =================== MOCK DATA SERVICE ===================

// 5. MockDataService.java (in service package)
package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.*;
import com.paves.employee_leave_management.dto.LeaveBalanceDTO;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MockDataService {

    private final List<Employee> mockEmployees;
    private final List<LeaveType> mockLeaveTypes;
    private final List<LeaveBalance> mockLeaveBalances;
    private final List<LeaveRequest> mockLeaveRequests;

    public MockDataService() {
        this.mockLeaveTypes = initializeLeaveTypes();
        this.mockEmployees = initializeEmployees();
        this.mockLeaveBalances = initializeLeaveBalances();
        this.mockLeaveRequests = initializeLeaveRequests();
    }

    private List<Employee> initializeEmployees() {
        List<Employee> employees = new ArrayList<>();

        // Manager
        Employee manager = Employee.builder()
                .employeeId("PAVEMP001")
                .firstName("John")
                .lastName("Manager")
                .email("john.manager@paves.com")
                .hireDate(LocalDate.of(2020, 1, 15))
                .jobTitle("Team Manager")
                .salary(new BigDecimal("75000"))
                .build();
        employees.add(manager);

        // Regular employees
        Employee emp1 = Employee.builder()
                .employeeId("PAVEMP002")
                .firstName("Alice")
                .lastName("Smith")
                .email("alice.smith@paves.com")
                .hireDate(LocalDate.of(2022, 3, 10))
                .jobTitle("Software Developer")
                .salary(new BigDecimal("60000"))
                .manager(manager)
                .build();
        employees.add(emp1);

        Employee emp2 = Employee.builder()
                .employeeId("PAVEMP003")
                .firstName("Bob")
                .lastName("Johnson")
                .email("bob.johnson@paves.com")
                .hireDate(LocalDate.of(2023, 6, 1))
                .jobTitle("Software Developer")
                .salary(new BigDecimal("55000"))
                .manager(manager)
                .build();
        employees.add(emp2);

        return employees;
    }

    private List<LeaveType> initializeLeaveTypes() {
        List<LeaveType> leaveTypes = new ArrayList<>();

        // Earned Leave
        LeaveType earnedLeave = LeaveType.builder()
                .leaveTypeId("L001")
                .leaveName("Earned Leave")
                .description("Annual vacation leave")
                .maxDaysPerYear(15)
                .maxCarryForward(12)
                .accrualRate(new BigDecimal("1.25"))
                .accrualFrequency("MONTHLY")
                .waitingPeriodDays(90)
                .pastDateLimitDays(0)
                .allowHalfDay(true)
                .allowNegativeBalance(false)
                .noticePeriodRestriction(true)
                .build();
        leaveTypes.add(earnedLeave);

        // Sick Leave
        LeaveType sickLeave = LeaveType.builder()
                .leaveTypeId("L002")
                .leaveName("Sick Leave")
                .description("Leave for medical reasons")
                .maxDaysPerYear(12)
                .maxCarryForward(0)
                .accrualRate(new BigDecimal("1.0"))
                .accrualFrequency("MONTHLY")
                .expiryDays(0)
                .pastDateLimitDays(28)
                .allowHalfDay(true)
                .allowNegativeBalance(false)
                .build();
        leaveTypes.add(sickLeave);

        // Comp Off
        LeaveType compOff = LeaveType.builder()
                .leaveTypeId("L003")
                .leaveName("Comp Off")
                .description("Compensatory leave for extra work")
                .maxDaysPerYear(0) // Unlimited
                .maxCarryForward(0)
                .accrualFrequency("MANUAL")
                .expiryDays(0)
                .pastDateLimitDays(28)
                .allowHalfDay(true)
                .allowNegativeBalance(false)
                .build();
        leaveTypes.add(compOff);

        return leaveTypes;
    }

    private List<LeaveBalance> initializeLeaveBalances() {
        List<LeaveBalance> balances = new ArrayList<>();
        int year = LocalDate.now().getYear();

        for (Employee emp : mockEmployees) {
            for (LeaveType leaveType : mockLeaveTypes) {
                LeaveBalance balance = LeaveBalance.builder()
                        .balanceId("BAL" + UUID.randomUUID().toString().substring(0, 8))
                        .employee(emp)
                        .leaveType(leaveType)
                        .year(year)
                        .build();

                // Set specific values based on leave type
                switch (leaveType.getLeaveName()) {
                    case "Earned Leave":
                        balance.setTotalLeaves(15);
                        balance.setAccruedLeaves(12);
                        balance.setUsedLeaves(5);
                        balance.setCarriedForward(2);
                        break;
                    case "Sick Leave":
                        balance.setTotalLeaves(12);
                        balance.setAccruedLeaves(8);
                        balance.setUsedLeaves(2);
                        balance.setCarriedForward(0);
                        break;
                    case "Comp Off":
                        balance.setTotalLeaves(5);
                        balance.setAccruedLeaves(5);
                        balance.setUsedLeaves(1);
                        balance.setCarriedForward(0);
                        break;
                }

                balance.updateRemainingLeaves();
                balances.add(balance);
            }
        }

        return balances;
    }

    private List<LeaveRequest> initializeLeaveRequests() {
        List<LeaveRequest> requests = new ArrayList<>();

        // Add some existing approved requests for overlap testing
        LeaveRequest existingRequest = LeaveRequest.builder()
                .leaveId("LR001")
                .employee(mockEmployees.get(1)) // Alice
                .leaveType(mockLeaveTypes.get(0)) // Earned Leave
                .startDate(LocalDate.now().plusDays(10))
                .endDate(LocalDate.now().plusDays(12))
                .daysRequested(3)
                .reason("Family vacation")
                .status(LeaveStatus.APPROVED)
                .requestDate(LocalDate.now().minusDays(5))
                .build();
        requests.add(existingRequest);

        return requests;
    }

    // Public methods for accessing mock data
    public List<Employee> getAllEmployees() {
        return new ArrayList<>(mockEmployees);
    }

    public Employee getEmployeeById(String employeeId) {
        return mockEmployees.stream()
                .filter(emp -> emp.getEmployeeId().equals(employeeId))
                .findFirst()
                .orElse(null);
    }

    public LeaveType getLeaveTypeById(String leaveTypeId) {
        return mockLeaveTypes.stream()
                .filter(lt -> lt.getLeaveTypeId().equals(leaveTypeId))
                .findFirst()
                .orElse(null);
    }

    public LeaveBalanceDTO getLeaveBalance(String employeeId, String leaveTypeId, Integer year) {
        LeaveBalance balance = mockLeaveBalances.stream()
                .filter(lb -> lb.getEmployee().getEmployeeId().equals(employeeId) &&
                        lb.getLeaveType().getLeaveTypeId().equals(leaveTypeId) &&
                        lb.getYear().equals(year))
                .findFirst()
                .orElse(null);

        if (balance == null) return null;

        return LeaveBalanceDTO.builder()
                .balanceId(balance.getBalanceId())
                .employeeId(balance.getEmployee().getEmployeeId())
                .employeeName(balance.getEmployee().getFullName())
                .leaveTypeId(balance.getLeaveType().getLeaveTypeId())
                .leaveTypeName(balance.getLeaveType().getLeaveName())
                .totalLeaves(balance.getTotalLeaves())
                .accruedLeaves(balance.getAccruedLeaves())
                .usedLeaves(balance.getUsedLeaves())
                .remainingLeaves(balance.getRemainingLeaves())
                .carriedForward(balance.getCarriedForward())
//                .availableBalance(balance.getAvailableBalance())
                .year(balance.getYear())
                .build();
    }

    public List<LeaveRequest> getOverlappingRequests(String employeeId, LocalDate startDate, LocalDate endDate) {
        return mockLeaveRequests.stream()
                .filter(lr -> lr.getEmployee().getEmployeeId().equals(employeeId) &&
                        lr.getStatus() == LeaveStatus.APPROVED &&
                        isDateOverlapping(lr.getStartDate(), lr.getEndDate(), startDate, endDate))
                .collect(Collectors.toList());
    }

    private boolean isDateOverlapping(LocalDate existingStart, LocalDate existingEnd,
                                      LocalDate newStart, LocalDate newEnd) {
        return newStart.isBefore(existingEnd.plusDays(1)) && newEnd.isAfter(existingStart.minusDays(1));
    }

    public boolean isManager(String managerId, String employeeId) {
        Employee employee = getEmployeeById(employeeId);
        return employee != null && employee.getManager() != null &&
                employee.getManager().getEmployeeId().equals(managerId);
    }
}

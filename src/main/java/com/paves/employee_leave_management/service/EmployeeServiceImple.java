package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.daoInterface.EmployeeDAO;
import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.EmployeesDTO;
import com.paves.employee_leave_management.dto.UserDTOFromUMS;
import com.paves.employee_leave_management.dto.UserResponseDTO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.enums.EmployeeStatus;
import com.paves.employee_leave_management.globalExceptionHandler.EmployeeExceptionHandler;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.serviceInterface.EmployeeServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmployeeServiceImple implements EmployeeServiceInterface {

    @Autowired
    EmployeeDAO employeeDAO;

    @Autowired
    LeaveBalanceServiceInterface leaveBalanceService;

    @Autowired
    EmployeeRepo employeeRepo;

    private final WebClient webClient;

    public EmployeeServiceImple(WebClient webClient) {
        this.webClient = webClient;
    }


    @Override
    public ResponseEntity<Employee> saveEmployee(Employee employee) {
        Employee emp = employeeDAO.saveEmployee(employee);
        if (emp != null) {
            leaveBalanceService.createLeaveBalanceForNewEmployee(employee.getEmployeeId());
            return new ResponseEntity<Employee>(emp, HttpStatus.ACCEPTED);
        } else {
            throw new EmployeeExceptionHandler("Unable to save employee");
        }
    }

    @Override
    public ResponseEntity<Employee> updateEmployee(String employeeId, Employee employee) {

        Employee emp = employeeDAO.updateEmployee(employeeId, employee);
        if (emp != null) {
            return new ResponseEntity<Employee>(emp, HttpStatus.ACCEPTED);
        } else {
            throw new EmployeeExceptionHandler("Employee not found with id: " + employeeId);
        }

    }

    @Override
    public ResponseEntity<Employee> getByEmployeeId(String employeeId) {
        return employeeDAO.findByEmployeeId(employeeId).map(emp -> new ResponseEntity<Employee>(emp, HttpStatus.ACCEPTED)).orElseThrow(() -> new EmployeeExceptionHandler("Employee not found with id: " + employeeId));
    }

    @Override
    public List<Employee> getAllEmployees() {
        List<Employee> emps = employeeDAO.findAll();
        if (emps != null) {
            return emps;
        } else {
            throw new EmployeeExceptionHandler("No employees found");
        }
    }

    @Override
    public UserResponseDTO fetchUsers(String token) {
        return webClient.get()
                .uri("http://13.48.18.145/admin/users?page=1&limit=50&search=")
                .header("Authorization", token)
                .retrieve()
                .bodyToMono(UserResponseDTO.class)
                .block();
    }

    @Override
    public ResponseEntity<ApiResponse<Object>> addEmployees(String token) {
        UserResponseDTO userResponseDTO = fetchUsers(token);

        if(userResponseDTO == null || userResponseDTO.getTotal() == 0){
            return ResponseEntity.ok(
                    new ApiResponse<>(true, "No Users Found", new ArrayList<>())
            );
        }

        List<UserDTOFromUMS> users = userResponseDTO.getUsers();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        String dateString = "04/23/2025";
        LocalDate localDate = LocalDate.parse(dateString, formatter);

        // 1. Use a Set for faster lookup of existing IDs
        Set<String> existingEmpIds = employeeRepo.findAll()
                .stream()
                .map(Employee::getEmployeeId)
                .collect(Collectors.toSet());

        List<UserDTOFromUMS> addedUsers = new ArrayList<>();

        users.forEach(u -> {
            String currentUserId = String.valueOf(u.getUserId());

            // 2. Check if employee already exists
            if (!existingEmpIds.contains(currentUserId)) {

                // 3. IMPORTANT: Create a NEW instance for every save
                Employee newEmployee = new Employee();

                newEmployee.setEmployeeId(currentUserId);
                newEmployee.setFirstName(u.getFirstName());
                newEmployee.setLastName(u.getLastName());
                newEmployee.setEmail(u.getMail());
                newEmployee.setPassword(u.getPassword());
                newEmployee.setHireDate(localDate);
                newEmployee.setPhone(u.getContact());
                newEmployee.setGender(u.getGender());

                employeeDAO.saveEmployee(newEmployee);

                // Track who we actually added
                addedUsers.add(u);
                // Add to the set so if the API has duplicates, we don't add them twice
                existingEmpIds.add(currentUserId);
            }
        });

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Successfully processed users. Added " + addedUsers.size() + " new employees.",
                        addedUsers
                )
        );
    }

    @Override
    public List<EmployeesDTO> getAllEmployeePaginated() {
        PageRequest pageRequest = PageRequest.of(0, 10);
        Page<Employee> employees =  employeeRepo.findAll(pageRequest);
        List<EmployeesDTO> employeesDTOList = new ArrayList<>();
        for (Employee emp: employees){
            EmployeesDTO employeesDTO = new EmployeesDTO();
            employeesDTO.setEmployeeId(emp.getEmployeeId());
            employeesDTO.setName(emp.getFirstName() + " " + emp.getLastName());
            employeesDTOList.add(employeesDTO);
        }
        return employeesDTOList;
    }

    @Override
    public List<EmployeesDTO> searchEmployees(String search, int page, String managerId) {
        Pageable pageable = PageRequest.of(page, 10);
        // You should use the query created above to ensure Managers only see THEIR employees
        Page<Employee> employees = employeeRepo.searchManagedEmployees(search, managerId, pageable);

        return employees.stream().map(emp -> {
            EmployeesDTO dto = new EmployeesDTO();
            dto.setEmployeeId(emp.getEmployeeId());
            dto.setName(emp.getFirstName() + " " + emp.getLastName());
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public void handleDelete(String employeeId) {
        Optional<Employee> employee = employeeRepo.findByEmployeeUuid(employeeId);
        if (employee.isPresent()) {
            employee.get().setStatus(EmployeeStatus.INACTIVE);
            employeeRepo.save(employee.get());
        } else {
            throw new EmployeeExceptionHandler("Employee not found with id: " + employeeId);
        }
    }
}

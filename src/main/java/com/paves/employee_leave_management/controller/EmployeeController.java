package com.paves.employee_leave_management.controller;

import co.elastic.clients.elasticsearch.license.LicenseStatus;
import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.EmployeesDTO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.serviceInterface.EmployeeServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@CrossOrigin
@RestController
@RequestMapping("/api/employee")
public class EmployeeController {
    @Autowired
    EmployeeServiceInterface serviceInterface;


    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<Employee> registerEmployee(@RequestBody Employee employee) {
        return serviceInterface.saveEmployee(employee);
    }

    @PutMapping("/update/{employeeId}")
    @PreAuthorize("hasRole('HR') or @permissionService.isOwner(authentication, #employeeId)")
    public ResponseEntity<Employee> updateEmployee(@PathVariable String employeeId, @RequestBody Employee employee) {
        return serviceInterface.updateEmployee(employeeId, employee);
    }

    @PostMapping("/add-employees")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ApiResponse<Object>> addEmployees(@RequestHeader("Authorization") String token ){
        return serviceInterface.addEmployees(token);
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ApiResponse<Object>> getAllEmployees() {
        List<Employee> employees = serviceInterface.getAllEmployees();
        return ResponseEntity.ok(new ApiResponse<>(true, "All Employees", employees));
    }

    @GetMapping("/all-employees")
    @PreAuthorize("hasAnyRole('HR', 'MANAGER')")
    public ResponseEntity<ApiResponse<Object>> getAllEmployeePaginated(){
        List<EmployeesDTO> employee = serviceInterface.getAllEmployeePaginated();
        return ResponseEntity.ok(new ApiResponse<>(true, "All Employees", employee));
    }

    @GetMapping("/search/{managerId}")
    public ResponseEntity<ApiResponse<Object>> searchEmployees(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @PathVariable String managerId
    ) {
        List<EmployeesDTO> employees =  serviceInterface.searchEmployees(search, page, managerId);
        return ResponseEntity.ok(new ApiResponse<>(true, "All Employees", employees));
    }
}

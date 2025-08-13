package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.serviceInterface.EmployeeServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
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
    @PreAuthorize("hasAnyRole('HR')")
    public ResponseEntity<Employee> updateEmployee(@PathVariable String employeeId, @RequestBody Employee employee) {
        return serviceInterface.updateEmployee(employeeId, employee);
    }

    @PostMapping("test/Manager")
    @PreAuthorize("hasAnyRole('MANAGER')")
    public String testManager() {
        return "manager access granted";
    }

    @PostMapping("test/HR")
    @PreAuthorize("hasAnyRole('HR')")
    public String testHR() {
        return "hr access granted";
    }

    @PostMapping("test/General")
    @PreAuthorize("hasAnyRole('GENERAL')")
    public String testGeneral() {
        return "general access granted";
    }


}

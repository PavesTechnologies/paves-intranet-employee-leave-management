package com.paves.employee_leave_management.controller;


import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.serviceInterface.EmployeeServiceInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class EmployeeController
{
    @Autowired
    EmployeeServiceInterface serviceInterface;

    @PostMapping("/register")
    public ResponseEntity<Employee> registerEmployee(@RequestBody Employee employee)
    { return serviceInterface.saveEmployee(employee); } // TODO>

    @PutMapping("/update/{employeeId}")
    public ResponseEntity<Employee> updateEmployee(@PathVariable String employeeId,@RequestBody Employee employee)
    { return serviceInterface.updateEmployee(employeeId, employee); }
}

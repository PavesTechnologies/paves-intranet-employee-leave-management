package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.entities.Employee;
import org.springframework.http.ResponseEntity;

public interface EmployeeServiceInterface {
    ResponseEntity<Employee> saveEmployee(Employee employee);
    ResponseEntity<Employee> updateEmployee(String employeeId,Employee employee);
}

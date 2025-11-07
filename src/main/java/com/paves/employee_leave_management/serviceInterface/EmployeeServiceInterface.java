package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.entities.Employee;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface EmployeeServiceInterface {
    ResponseEntity<Employee> saveEmployee(Employee employee);

    ResponseEntity<Employee> updateEmployee(String employeeId, Employee employee);

    ResponseEntity<Employee> getByEmployeeId(String employeeId);

    ResponseEntity<List<Employee>> getAllEmployees();
}

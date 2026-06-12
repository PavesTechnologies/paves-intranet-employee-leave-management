package com.paves.employee_leave_management.serviceInterface;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.EmployeesDTO;
import com.paves.employee_leave_management.dto.UserResponseDTO;
import com.paves.employee_leave_management.entities.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface EmployeeServiceInterface {
    ResponseEntity<Employee> saveEmployee(Employee employee);

    ResponseEntity<Employee> updateEmployee(String employeeId, Employee employee);

    ResponseEntity<Employee> getByEmployeeId(String employeeId);

    List<Employee> getAllEmployees();

    UserResponseDTO fetchUsers(String token);

    ResponseEntity<ApiResponse<Object>> addEmployees(String token);

    List<EmployeesDTO> getAllEmployeePaginated();

    public List<EmployeesDTO> searchEmployees(String search, int page, String managerId);

    public void handleDelete(String employeeId);
}

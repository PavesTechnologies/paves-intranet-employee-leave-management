package com.paves.employee_leave_management.dao;

import com.paves.employee_leave_management.daoInterface.EmployeeDAO;
import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.globalExceptionHandler.EmployeeExceptionHandler;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import org.hibernate.PropertyValueException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;


@Repository
public class EmployeeDAOImple implements EmployeeDAO {

    @Autowired
    EmployeeRepo repo;
    @Override
    public Employee saveEmployee(Employee employee) {
        return (Employee) repo.findByEmployeeId(employee.getEmployeeId())
                .map(existing -> {
                    throw new EmployeeExceptionHandler("Employee already exists with ID: " + employee.getEmployeeId());
                })
                .orElseGet(() -> repo.save(employee));
    }

    @Override
    public Employee updateEmployee(String employeeId,Employee employee1) {
            return repo.findById(employeeId).map(existingEmployee -> {

                try {
                    return repo.save(employee1);
                } catch (DataIntegrityViolationException | PropertyValueException e) {
                    throw new EmployeeExceptionHandler("Required fields are missing or duplicate value exists");
                }

            }).orElseThrow(() -> new EmployeeExceptionHandler("Employee not found with ID: " + employeeId));
        }

    }


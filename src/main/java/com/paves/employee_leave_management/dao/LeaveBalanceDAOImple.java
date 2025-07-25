package com.paves.employee_leave_management.dao;

import com.paves.employee_leave_management.daoInterface.LeaveBalanceDAO;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;

import com.paves.employee_leave_management.entities.LeaveBalance;

import lombok.*;
import org.springframework.stereotype.*;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class LeaveBalanceDAOImple implements LeaveBalanceDAO {

    private final LeaveBalanceRepo leaveBalanceRepo;

    @Override
    public void save(LeaveBalance balance) {
        leaveBalanceRepo.save(balance);
    }

    @Override
    public boolean existsByEmployeeIdAndLeaveTypeIdAndYear(String empId, String leaveTypeId, int year) {
        return leaveBalanceRepo.existsByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(empId, leaveTypeId, year);
    }

    @Override
    public LeaveBalance findById(String balanceId) {
        return leaveBalanceRepo.findById(balanceId).orElse(null);
    }

    @Override
    public List<LeaveBalance> findAll() {
        return leaveBalanceRepo.findAll();
    }

    @Override
    public LeaveBalance findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(String employeeId,String leaveTypeId, int year) {
        return leaveBalanceRepo.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(employeeId, leaveTypeId, year);
    }
    @Override
    public List<LeaveBalance> findByEmployeeId(String employeeId) {
        return leaveBalanceRepo.findByEmployeeEmployeeId(employeeId);
    }

    @Override
    public List<LeaveBalance> findByLeaveId(String leaveId) {
        return leaveBalanceRepo.findByLeaveTypeLeaveTypeId(leaveId);
    }

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(
            String employeeId, String leaveTypeId, Integer year
    ) {
        String jpql = "SELECT lb FROM LeaveBalance lb " +
                "WHERE lb.employee.employeeId = :employeeId " +
                "AND lb.leaveType.leaveTypeId = :leaveTypeId " +
                "AND lb.year = :year";
        try {
            LeaveBalance result = entityManager.createQuery(jpql, LeaveBalance.class)
                    .setParameter("employeeId", employeeId)
                    .setParameter("leaveTypeId", leaveTypeId)
                    .setParameter("year", year)
                    .getSingleResult();
            return Optional.of(result);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}

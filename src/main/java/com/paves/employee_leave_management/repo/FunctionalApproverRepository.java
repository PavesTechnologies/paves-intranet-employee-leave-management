package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.FunctionalApprover;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FunctionalApproverRepository extends JpaRepository<FunctionalApprover, Long> {

    Optional<FunctionalApprover> findByDepartment(String department);
}

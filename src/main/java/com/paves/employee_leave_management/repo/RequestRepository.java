package com.paves.employee_leave_management.repo;


import com.paves.employee_leave_management.entities.Request;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface RequestRepository extends JpaRepository<Request, UUID> {
    List<Request> findByCreatedBy(UUID createdBy);
    List<Request> findByStatus(String status);
}

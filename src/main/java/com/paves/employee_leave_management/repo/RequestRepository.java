package com.paves.employee_leave_management.repo;


import com.paves.employee_leave_management.entities.Request;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

@Repository
public interface RequestRepository extends JpaRepository<Request, UUID> {
    List<Request> findByCreatedBy(String createdBy);
    List<Request> findByStatus(String status);

    Optional<Request> findByTargetEntityId(String leaveId);
}

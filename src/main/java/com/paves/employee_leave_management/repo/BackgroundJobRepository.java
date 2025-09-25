package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.BackgroundJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, String> {}

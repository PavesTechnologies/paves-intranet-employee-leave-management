package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, String> {

    // Fetch all groups for a department
    List<Group> findByDepartmentId(String departmentId);

    // Fetch group by head (for approval resolution)
    Optional<Group> findByHeadEmployeeId(String headId);

    // Fetch group by name
    Optional<Group> findByName(String name);
}

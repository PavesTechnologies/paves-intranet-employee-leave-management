package com.paves.employee_leave_management.repo;

import com.paves.employee_leave_management.entities.RuleSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RuleSetRepository extends JpaRepository<RuleSet, UUID> {

    Optional<RuleSet> findByName(String name);

    List<RuleSet> findByActiveTrue();
}

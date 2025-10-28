package com.paves.employee_leave_management.controller;


import com.paves.employee_leave_management.enums.ActionType;
import com.paves.employee_leave_management.enums.ApproverType;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/rule-book")
public class RuleBookController {
    @GetMapping("/action-types")
    @Cacheable("action-types")
    public List<String> getAllActionTypes() {
        // Converts enum constants to a list of strings
        return Arrays.stream(ActionType.values())
                .map(Enum::name)
                .toList();
    }

    @GetMapping("/approver-types")
    @Cacheable("approver-types")
    public List<String> getAllApproverTypes() {
        return Arrays.stream(ApproverType.values())
                .map(Enum::name)
                .toList();
    }


}

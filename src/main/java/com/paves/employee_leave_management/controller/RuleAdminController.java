package com.paves.employee_leave_management.controller;

import com.paves.employee_leave_management.dto.ApiResponse;
import com.paves.employee_leave_management.dto.ApprovalStepDTO;
import com.paves.employee_leave_management.dto.RuleConditionDTO;
import com.paves.employee_leave_management.dto.RuleSetDTO;
import com.paves.employee_leave_management.serviceInterface.RuleConfigService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflow/admin") // Base path for admin config
@RequiredArgsConstructor
@Slf4j
@CrossOrigin // Add if needed
@PreAuthorize("hasRole('ADMIN') or hasRole('HR_ADMIN')") // Secure all endpoints in this controller
public class RuleAdminController {

    private final RuleConfigService ruleConfigService;

    // --- RuleSet Endpoints ---

    @GetMapping("/rulesets")
    public ResponseEntity<ApiResponse<List<RuleSetDTO>>> getAllRuleSets() {
        try {
            List<RuleSetDTO> ruleSets = ruleConfigService.getAllRuleSets();
            return ResponseEntity.ok(new ApiResponse<>(true, "RuleSets retrieved successfully.", ruleSets));
        } catch (Exception e) {
            log.error("Error retrieving rulesets", e);
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false, "Failed to retrieve rulesets.", null));
        }
    }

    @GetMapping("/rulesets/{ruleSetId}")
    public ResponseEntity<ApiResponse<RuleSetDTO>> getRuleSetById(@PathVariable UUID ruleSetId) {
        try {
            RuleSetDTO ruleSet = ruleConfigService.getRuleSetById(ruleSetId);
            return ResponseEntity.ok(new ApiResponse<>(true, "RuleSet retrieved.", ruleSet));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error retrieving ruleset {}", ruleSetId, e);
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false, "Failed to retrieve ruleset.", null));
        }
    }

    @PostMapping("/rulesets")
    public ResponseEntity<ApiResponse<RuleSetDTO>> createRuleSet(@Valid @RequestBody RuleSetDTO ruleSetDto) {
        try {
            RuleSetDTO created = ruleConfigService.createRuleSet(ruleSetDto);
            return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "RuleSet created successfully.", created));
        } catch (Exception e) { // Catch potential unique constraint violations etc.
            log.error("Error creating ruleset", e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Failed to create ruleset: " + e.getMessage(), null));
        }
    }

    @PutMapping("/rulesets/{ruleSetId}")
    public ResponseEntity<ApiResponse<RuleSetDTO>> updateRuleSet(@PathVariable UUID ruleSetId, @Valid @RequestBody RuleSetDTO ruleSetDto) {
        try {
            RuleSetDTO updated = ruleConfigService.updateRuleSet(ruleSetId, ruleSetDto);
            return ResponseEntity.ok(new ApiResponse<>(true, "RuleSet updated successfully.", updated));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error updating ruleset {}", ruleSetId, e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Failed to update ruleset: " + e.getMessage(), null));
        }
    }

    @PatchMapping("/rulesets/{ruleSetId}/activate")
    public ResponseEntity<ApiResponse<RuleSetDTO>> activateRuleSet(@PathVariable UUID ruleSetId) {
        try {
            RuleSetDTO updated = ruleConfigService.activateRuleSet(ruleSetId);
            return ResponseEntity.ok(new ApiResponse<>(true, "RuleSet activated.", updated));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error activating RuleSet {}", ruleSetId, e);
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false, "Failed to activate RuleSet.", null));
        }
    }

    @PatchMapping("/rulesets/{ruleSetId}/deactivate")
    public ResponseEntity<ApiResponse<RuleSetDTO>> deactivateRuleSet(@PathVariable UUID ruleSetId) {
        try {
            RuleSetDTO updated = ruleConfigService.deactivateRuleSet(ruleSetId);
            return ResponseEntity.ok(new ApiResponse<>(true, "RuleSet deactivated.", updated));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error deactivating RuleSet {}", ruleSetId, e);
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false, "Failed to deactivate RuleSet.", null));
        }
    }


    @DeleteMapping("/rulesets/{ruleSetId}")
    public ResponseEntity<ApiResponse<Object>> deleteRuleSet(@PathVariable UUID ruleSetId) {
        try {
            ruleConfigService.deleteRuleSet(ruleSetId);
            return ResponseEntity.ok(new ApiResponse<>(true, "RuleSet deleted successfully.", null));
            // return ResponseEntity.noContent().build(); // Alternative RESTful response
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error deleting ruleset {}", ruleSetId, e);
            // Consider catching DataIntegrityViolationException if ruleset is in use
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false, "Failed to delete ruleset.", null));
        }
    }

    // --- RuleCondition Endpoints ---

    @GetMapping("/rulesets/{ruleSetId}/conditions")
    public ResponseEntity<ApiResponse<List<RuleConditionDTO>>> getConditionsForRuleSet(@PathVariable UUID ruleSetId) {
        try {
            List<RuleConditionDTO> conditions = ruleConfigService.getConditionsForRuleSet(ruleSetId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Conditions retrieved.", conditions));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error retrieving conditions for ruleset {}", ruleSetId, e);
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false, "Failed to retrieve conditions.", null));
        }
    }

    @PostMapping("/rulesets/{ruleSetId}/conditions")
    public ResponseEntity<ApiResponse<RuleConditionDTO>> addConditionToRuleSet(
            @PathVariable UUID ruleSetId,
            @Valid @RequestBody RuleConditionDTO conditionDto) {
        try {
            RuleConditionDTO created = ruleConfigService.addConditionToRuleSet(ruleSetId, conditionDto);
            return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Condition added.", created));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error adding condition to ruleset {}", ruleSetId, e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Failed to add condition: " + e.getMessage(), null));
        }
    }

    @PutMapping("/conditions/{conditionId}")
    public ResponseEntity<ApiResponse<RuleConditionDTO>> updateCondition(
            @PathVariable UUID conditionId,
            @Valid @RequestBody RuleConditionDTO conditionDto) {
        try {
            RuleConditionDTO updated = ruleConfigService.updateCondition(conditionId, conditionDto);
            return ResponseEntity.ok(new ApiResponse<>(true, "Condition updated.", updated));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error updating condition {}", conditionId, e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Failed to update condition: " + e.getMessage(), null));
        }
    }


    @DeleteMapping("/conditions/{conditionId}")
    public ResponseEntity<ApiResponse<Object>> deleteCondition(@PathVariable UUID conditionId) {
        try {
            ruleConfigService.deleteCondition(conditionId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Condition deleted successfully.", null));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error deleting condition {}", conditionId, e);
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false, "Failed to delete condition.", null));
        }
    }

    // --- ApprovalStep Endpoints ---

    @GetMapping("/rulesets/{ruleSetId}/steps")
    public ResponseEntity<ApiResponse<List<ApprovalStepDTO>>> getStepsForRuleSet(@PathVariable UUID ruleSetId) {
        try {
            List<ApprovalStepDTO> steps = ruleConfigService.getStepsForRuleSet(ruleSetId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Steps retrieved.", steps));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error retrieving steps for ruleset {}", ruleSetId, e);
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false, "Failed to retrieve steps.", null));
        }
    }


    @PostMapping("/rulesets/{ruleSetId}/steps")
    public ResponseEntity<ApiResponse<ApprovalStepDTO>> addStepToRuleSet(
            @PathVariable UUID ruleSetId,
            @Valid @RequestBody ApprovalStepDTO stepDto) {
        try {
            ApprovalStepDTO created = ruleConfigService.addStepToRuleSet(ruleSetId, stepDto);
            return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse<>(true, "Step added.", created));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error adding step to ruleset {}", ruleSetId, e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Failed to add step: " + e.getMessage(), null));
        }
    }

    @PutMapping("/steps/{stepId}")
    public ResponseEntity<ApiResponse<ApprovalStepDTO>> updateStep(
            @PathVariable UUID stepId,
            @Valid @RequestBody ApprovalStepDTO stepDto) {
        try {
            ApprovalStepDTO updated = ruleConfigService.updateStep(stepId, stepDto);
            return ResponseEntity.ok(new ApiResponse<>(true, "Step updated.", updated));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error updating step {}", stepId, e);
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, "Failed to update step: " + e.getMessage(), null));
        }
    }

    @DeleteMapping("/steps/{stepId}")
    public ResponseEntity<ApiResponse<Object>> deleteStep(@PathVariable UUID stepId) {
        try {
            ruleConfigService.deleteStep(stepId);
            return ResponseEntity.ok(new ApiResponse<>(true, "Step deleted successfully.", null));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            log.error("Error deleting step {}", stepId, e);
            return ResponseEntity.internalServerError().body(new ApiResponse<>(false, "Failed to delete step.", null));
        }
    }
}
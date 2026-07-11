package com.paves.employee_leave_management.service;

import com.paves.employee_leave_management.entities.Employee;
import com.paves.employee_leave_management.entities.LeaveBalance;
import com.paves.employee_leave_management.entities.LeaveBalanceJob;
import com.paves.employee_leave_management.entities.LeaveType;
import com.paves.employee_leave_management.repo.EmployeeRepo;
import com.paves.employee_leave_management.repo.LeaveBalanceJobRepository;
import com.paves.employee_leave_management.repo.LeaveBalanceRepo;
import com.paves.employee_leave_management.repo.LeaveTypeRepo;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceJobServiceInterface;
import com.paves.employee_leave_management.serviceInterface.LeaveBalanceServiceInterface;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class LeaveBalanceJobServiceImplementation implements LeaveBalanceJobServiceInterface {

    private final LeaveBalanceJobRepository jobRepository;
    private final EmployeeRepo employeeRepository;
    private final LeaveTypeRepo leaveTypeRepo;
    private final LeaveBalanceRepo leaveBalanceRepo;
    private final LeaveBalanceServiceInterface leaveBalanceService;
    private final CacheManager cacheManager;

    public LeaveBalanceJobServiceImplementation(LeaveBalanceJobRepository jobRepository, EmployeeRepo employeeRepository,
                                                LeaveTypeRepo leaveTypeRepo, LeaveBalanceRepo leaveBalanceRepo, LeaveBalanceServiceInterface leaveBalanceService,
                                                CacheManager cacheManager){
        this.jobRepository = jobRepository;
        this.employeeRepository = employeeRepository;
        this.leaveTypeRepo = leaveTypeRepo;
        this.leaveBalanceRepo = leaveBalanceRepo;
        this.leaveBalanceService = leaveBalanceService;
        this.cacheManager = cacheManager;
    }


    @Async("leaveBalanceExecutor")
    @Override
    public void processLeaveBalancesAsync(String jobId, String leaveTypeId) {
        LeaveBalanceJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        LeaveType leaveType = leaveTypeRepo.findByLeaveTypeId(leaveTypeId)
                .orElseThrow(() -> new RuntimeException("Leave type not found: " + leaveTypeId));

        List<Employee> employees = employeeRepository.findAll();
        int total = employees.size();
        int year = java.time.LocalDate.now().getYear();

        // update job to RUNNING
        updateJobStatus(jobId, LeaveBalanceJob.JobStatus.RUNNING, total, 0);

        log.info("Starting leave balance job {} for {} employees", jobId, total);

        int processed = 0;
        List<String> createdBalanceIds = new java.util.ArrayList<>();

        try {
            for (Employee employee : employees) {
                // create balance for one employee
                LeaveBalance balance = createSingleBalance(employee, leaveType);
                if (balance != null) {
                    createdBalanceIds.add(balance.getBalanceId());
                }

                // Evict this employee's cached balance/dropdown entries immediately, rather than
                // waiting for the whole job to finish — otherwise a read for this employee that
                // lands between "job started" and "job completed" caches a stale/empty result
                // that the job's own end-of-run eviction never clears (it only clears the two
                // coarser caches below, not employeeLeaveBalanceForDropdown).
                evictEmployeeBalanceCaches(employee.getEmployeeId(), year);

                processed++;

                // update progress every 10 employees
                if (processed % 10 == 0 || processed == total) {
                    int percentage = (int) ((processed * 100.0) / total);
                    updateProgress(jobId, processed, percentage);
                    log.info("Job {} progress: {}/{} ({}%)", jobId, processed, total, percentage);
                }
            }

            // all done — mark complete
            markCompleted(jobId, processed);
            log.info("Job {} completed successfully. {} balances created.", jobId, processed);

            // evict caches after all balances created
            cacheManager.getCache("employeeLeaveBalance").clear();
            cacheManager.getCache("employeesLeaveBalances").clear();
            log.info("Cache evicted after job {} completion.", jobId);

        } catch (Exception e) {
            log.error("Job {} failed at employee {}/{}: {}", jobId, processed, total, e.getMessage());

            // rollback — delete all created balances
            rollback(jobId, leaveTypeId, createdBalanceIds, e.getMessage());
        }
    }


    // creates balance for one employee in its own transaction
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LeaveBalance createSingleBalance(Employee employee, LeaveType leaveType) {
        try {
            // check if balance already exists
            boolean exists = leaveBalanceRepo
                    .findByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(
                            employee.getEmployeeId(),
                            leaveType.getLeaveTypeId(),
                            java.time.LocalDate.now().getYear())
                    .isPresent();

            if (exists) return null;

            // reuse your existing balance creation logic
            leaveBalanceService.createLeaveBalanceForNewEmployee(employee.getEmployeeId());
            return leaveBalanceRepo
                    .findByEmployeeEmployeeIdAndLeaveTypeLeaveTypeIdAndYear(
                            employee.getEmployeeId(),
                            leaveType.getLeaveTypeId(),
                            java.time.LocalDate.now().getYear()
                    ).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to create balance for employee {}: {}",
                    employee.getEmployeeId(), e.getMessage());
            throw e;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJobStatus(String jobId, LeaveBalanceJob.JobStatus status,
                                int total, int processed) {
        LeaveBalanceJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(status);
        job.setTotalEmployees(total);
        job.setProcessedEmployees(processed);
        jobRepository.save(job);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProgress(String jobId, int processed, int percentage) {
        LeaveBalanceJob job = jobRepository.findById(jobId).orElseThrow();
        job.setProcessedEmployees(processed);
        job.setProgressPercentage(percentage);
        jobRepository.save(job);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(String jobId, int processed) {
        LeaveBalanceJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(LeaveBalanceJob.JobStatus.COMPLETED);
        job.setProcessedEmployees(processed);
        job.setProgressPercentage(100);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollback(String jobId, String leaveTypeId,
                         List<String> createdBalanceIds, String errorMessage) {
        log.warn("Rolling back job {} — deleting {} created balances",
                jobId, createdBalanceIds.size());

        // delete all balances created by this job
        createdBalanceIds.forEach(balanceId -> {
            try {
                leaveBalanceRepo.deleteById(balanceId);
            } catch (Exception e) {
                log.error("Failed to rollback balance {}: {}", balanceId, e.getMessage());
            }
        });

        // deactivate the leave type since balances couldn't be created
        leaveTypeRepo.findByLeaveTypeId(leaveTypeId).ifPresent(lt -> {
            lt.setActive(false);
            leaveTypeRepo.save(lt);
        });

        // mark job as rolled back
        LeaveBalanceJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(LeaveBalanceJob.JobStatus.ROLLED_BACK);
        job.setErrorMessage(errorMessage);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);

        log.warn("Job {} rolled back. Leave type {} deactivated.", jobId, leaveTypeId);
    }

    public LeaveBalanceJob getJobStatus(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));
    }

    private void evictEmployeeBalanceCaches(String employeeId, int year) {
        String key = employeeId + "-" + year;
        try {
            var balanceCache = cacheManager.getCache("employeeLeaveBalance");
            if (balanceCache != null) balanceCache.evict(key);

            var dropdownCache = cacheManager.getCache("employeeLeaveBalanceForDropdown");
            if (dropdownCache != null) dropdownCache.evict(key);
        } catch (Exception e) {
            log.warn("Failed to evict balance caches for employee {}: {}", employeeId, e.getMessage());
        }
    }
}

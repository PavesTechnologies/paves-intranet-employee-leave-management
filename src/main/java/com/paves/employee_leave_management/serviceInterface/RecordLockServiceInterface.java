package com.paves.employee_leave_management.serviceInterface;

public interface RecordLockServiceInterface {

    String lockRecord(String tableName, String recordId, String lockedBy);


    void releaseLock(String tableName, String recordId, String lockedBy);


    boolean isLocked(String tableName, String recordId);


    String getLockedBy(String tableName, String recordId);
}

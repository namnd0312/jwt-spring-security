package com.namnd.springjwt.service;

import com.namnd.springjwt.model.User;

public interface AccountLockService {
    void registerFailedAttempt(String email);
    void resetFailedAttempts(String email);
    boolean unlockIfExpired(User user);
    boolean isLocked(User user);
    long getRemainingLockTimeMs(User user);
}

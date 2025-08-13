package com.scanales.eventflow.service;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Tracks memory and disk capacity for user data and decides if new logins can be admitted.
 */
@ApplicationScoped
public class CapacityService {

    public enum Mode { ADMITTING, CONTAINING }
    public enum Trend { STABLE, UP, DOWN }

    public record Status(
            Mode mode,
            int activeUsers,
            double memoryPercent,
            double diskFreePercent,
            Instant lastEvaluation,
            Trend trend) {}

    @Inject
    PersistenceService persistence;

    private final Set<String> admittedUsers = ConcurrentHashMap.newKeySet();
    private volatile double lastMemory = 0;
    private volatile double lastDiskFree = 0;
    private volatile Status lastStatus = new Status(Mode.ADMITTING, 0, 0, 0, Instant.EPOCH, Trend.STABLE);

    /** Evaluates current capacity and returns the status. */
    public synchronized Status evaluate() {
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long max = Runtime.getRuntime().maxMemory();
        double mem = max == 0 ? 0 : ((double) used / max) * 100d;
        double diskFree = 100d - persistence.getDiskUsage();

        Trend t = Trend.STABLE;
        if (mem > lastMemory + 1 || diskFree < lastDiskFree - 1) {
            t = Trend.UP;
        } else if (mem < lastMemory - 1 || diskFree > lastDiskFree + 1) {
            t = Trend.DOWN;
        }
        lastMemory = mem;
        lastDiskFree = diskFree;

        Mode m = canAdmit(mem, diskFree) ? Mode.ADMITTING : Mode.CONTAINING;
        lastStatus = new Status(m, admittedUsers.size(), mem, diskFree, Instant.now(), t);
        return lastStatus;
    }

    /** Returns the last computed status. */
    public Status getStatus() {
        return lastStatus;
    }

    /** Marks a user as admitted. */
    public void markAdmitted(String email) {
        if (email != null) {
            admittedUsers.add(email);
        }
    }

    /** Checks if the user was previously admitted. */
    public boolean isAdmitted(String email) {
        return email != null && admittedUsers.contains(email);
    }

    private boolean canAdmit(double memPercent, double diskFreePercent) {
        return memPercent < 80 && diskFreePercent > 10;
    }
}


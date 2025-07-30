package com.scanales.eventflow.service;

import java.time.LocalDateTime;

public class GitLoadStatus {
    private boolean success;
    private String message;
    private int filesRead;
    private int eventsImported;
    private String repoUrl;
    private String branch;
    private LocalDateTime lastAttempt;
    private LocalDateTime lastSuccess;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getFilesRead() { return filesRead; }
    public void setFilesRead(int filesRead) { this.filesRead = filesRead; }

    public int getEventsImported() { return eventsImported; }
    public void setEventsImported(int eventsImported) { this.eventsImported = eventsImported; }

    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public LocalDateTime getLastAttempt() { return lastAttempt; }
    public void setLastAttempt(LocalDateTime lastAttempt) { this.lastAttempt = lastAttempt; }

    public LocalDateTime getLastSuccess() { return lastSuccess; }
    public void setLastSuccess(LocalDateTime lastSuccess) { this.lastSuccess = lastSuccess; }
}

package com.scanales.eventflow.service;

import java.util.List;

/** Result details for Git troubleshooting operations. */
public class GitTroubleshootResult {
    private boolean repoAccessible;
    private boolean cloneSuccess;
    private int jsonFiles;
    private int validJson;
    private List<String> invalidFiles;
    private String message;
    private String errorDetails;

    public boolean isRepoAccessible() { return repoAccessible; }
    public void setRepoAccessible(boolean repoAccessible) { this.repoAccessible = repoAccessible; }

    public boolean isCloneSuccess() { return cloneSuccess; }
    public void setCloneSuccess(boolean cloneSuccess) { this.cloneSuccess = cloneSuccess; }

    public int getJsonFiles() { return jsonFiles; }
    public void setJsonFiles(int jsonFiles) { this.jsonFiles = jsonFiles; }

    public int getValidJson() { return validJson; }
    public void setValidJson(int validJson) { this.validJson = validJson; }

    public List<String> getInvalidFiles() { return invalidFiles; }
    public void setInvalidFiles(List<String> invalidFiles) { this.invalidFiles = invalidFiles; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorDetails() { return errorDetails; }
    public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }
}

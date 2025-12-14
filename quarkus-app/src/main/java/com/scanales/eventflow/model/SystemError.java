package com.scanales.eventflow.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SystemError {

    public String id;
    public Instant createdAt;
    public String severity; // ERROR, WARN
    public String source; // Class or Component name
    public String message;
    public String stackTrace;
    public String userId; // Optional
    public boolean resolved;

    public SystemError() {
    }

    @JsonCreator
    public SystemError(
            @JsonProperty("id") String id,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("severity") String severity,
            @JsonProperty("source") String source,
            @JsonProperty("message") String message,
            @JsonProperty("stackTrace") String stackTrace,
            @JsonProperty("userId") String userId,
            @JsonProperty("resolved") boolean resolved) {
        this.id = id;
        this.createdAt = createdAt;
        this.severity = severity;
        this.source = source;
        this.message = message;
        this.stackTrace = stackTrace;
        this.userId = userId;
        this.resolved = resolved;
    }
}

package com.scanales.eventflow.cfp;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record CfpPresentationAsset(
    @JsonProperty("file_name") String fileName,
    @JsonProperty("content_type") String contentType,
    @JsonProperty("size_bytes") long sizeBytes,
    @JsonProperty("storage_path") String storagePath,
    @JsonProperty("uploaded_by_user_id") String uploadedByUserId,
    @JsonProperty("uploaded_at") Instant uploadedAt) {}


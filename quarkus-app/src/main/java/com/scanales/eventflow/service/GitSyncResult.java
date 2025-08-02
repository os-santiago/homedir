package com.scanales.eventflow.service;

import java.util.ArrayList;
import java.util.List;

public class GitSyncResult {
    public boolean success;
    public String message;
    public List<String> filesLoaded = new ArrayList<>();
    public List<String> errors = new ArrayList<>();
}


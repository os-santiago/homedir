package com.scanales.homedir.model;

/** Combines a talk with its parent event for reuse across the application. */
public record TalkInfo(Talk talk, Event event) {}

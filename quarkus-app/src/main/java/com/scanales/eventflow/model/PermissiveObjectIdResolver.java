package com.scanales.eventflow.model;

import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.fasterxml.jackson.annotation.ObjectIdResolver;
import com.fasterxml.jackson.annotation.SimpleObjectIdResolver;

/**
 * An {@link ObjectIdResolver} that ignores attempts to bind the same id to
 * multiple objects. This is useful when deserializing graphs where references
 * to the same object are repeated, avoiding "Already had POJO for id" errors.
 */
public class PermissiveObjectIdResolver extends SimpleObjectIdResolver {
    @Override
    public void bindItem(ObjectIdGenerator.IdKey id, Object pojo) {
        // Only bind if this id has not been seen before. If it's already
        // associated with an object, ignore the new binding to keep the first.
        if (resolveId(id) == null) {
            super.bindItem(id, pojo);
        }
    }
}

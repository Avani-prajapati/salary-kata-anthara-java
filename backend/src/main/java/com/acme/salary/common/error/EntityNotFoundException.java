package com.acme.salary.common.error;

public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String entityName, Object id) {
        super(entityName + " " + id + " was not found");
    }
}

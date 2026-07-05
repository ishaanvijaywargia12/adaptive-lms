package com.lms.common.exception;

public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(String subdomain) {
        super("Tenant not found for subdomain: " + subdomain);
    }
}

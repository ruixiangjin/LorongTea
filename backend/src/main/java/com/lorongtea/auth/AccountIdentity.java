package com.lorongtea.auth;

public record AccountIdentity(long userId, Role role, Long storeId) {
    public enum Role {
        CUSTOMER,
        STAFF
    }
}

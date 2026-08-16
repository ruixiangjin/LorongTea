package com.lorongtea.auth;

import java.security.Principal;

public record AccountIdentity(long userId, Role role, Long storeId) implements Principal {
    @Override
    public String getName() {
        return Long.toString(userId);
    }

    public enum Role {
        CUSTOMER,
        STAFF
    }
}

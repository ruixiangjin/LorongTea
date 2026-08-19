package com.lorongtea.catalog;

import com.lorongtea.api.ApiException;
import com.lorongtea.auth.AccountIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class StaffStoreAccess {
    private final StoreMapper stores;

    StaffStoreAccess(StoreMapper stores) {
        this.stores = stores;
    }

    Store assignedStore(AccountIdentity account) {
        if (account.role() != AccountIdentity.Role.STAFF) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "This role cannot access the resource");
        }

        Store store = stores.selectById(account.storeId());
        if (store == null || !store.isActive()) {
            throw new StoreNotFoundException();
        }
        return store;
    }

    void requireStore(AccountIdentity account, long storeId) {
        if (assignedStore(account).getId() != storeId) {
            throw new StoreNotFoundException();
        }
    }
}

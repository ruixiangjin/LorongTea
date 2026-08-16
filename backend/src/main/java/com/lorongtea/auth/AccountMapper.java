package com.lorongtea.auth;

import com.lorongtea.auth.AccountIdentity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
interface AccountMapper {
    @Select("""
            SELECT id, password_hash, role, store_id
            FROM app_user
            WHERE username = #{username} AND active = TRUE
            """)
    Account findActiveByUsername(@Param("username") String username);

    record Account(long id, String passwordHash, Role role, Long storeId) {
    }
}

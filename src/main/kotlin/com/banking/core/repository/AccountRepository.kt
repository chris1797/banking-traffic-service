package com.banking.core.repository

import com.banking.core.domain.AccountEntity
import org.springframework.data.jpa.repository.JpaRepository

interface AccountRepository: JpaRepository<AccountEntity, Long> {
    fun existsByAccountNumber(accountNumber: String): Boolean
    fun findByAccountNumber(accountNumber: String): AccountEntity?
}

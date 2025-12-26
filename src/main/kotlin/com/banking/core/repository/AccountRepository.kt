package com.banking.core.repository

import com.banking.core.domain.Account
import org.springframework.data.jpa.repository.JpaRepository

interface AccountRepository: JpaRepository<Account, Long> {
    fun existsByAccountNumber(accountNumber: String): Boolean
}

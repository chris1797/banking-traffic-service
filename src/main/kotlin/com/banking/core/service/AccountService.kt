package com.banking.core.service

import com.banking.core.domain.Account
import com.banking.core.repository.AccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
class AccountService(
    private val accountRepository: AccountRepository
) {
    @Transactional
    fun createAccount(holderName: String, initialBalance: BigDecimal = BigDecimal.ZERO): Account {
        val accountNumber = generateAccountNumber()
        val account = Account(
            accountNumber = accountNumber,
            holderName = holderName,
            balance = initialBalance
        )
        return accountRepository.save(account)
    }

    private fun generateAccountNumber(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12)
    }
}

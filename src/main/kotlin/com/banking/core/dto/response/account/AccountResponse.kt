package com.banking.core.dto.response.account

import com.banking.core.domain.Account
import com.banking.core.domain.EntityStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class AccountResponse(
    val id: Long,
    val status: EntityStatus = EntityStatus.ACTIVE,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val accountNumber: String,
    val holderName: String,
    val balance: BigDecimal
) {
    companion object {
        fun from(account: Account): AccountResponse {
            return AccountResponse(
                id = account.id,
                status = account.status,
                createdAt = account.createdAt,
                updatedAt = account.updatedAt,
                accountNumber = account.accountNumber,
                holderName = account.holderName,
                balance = account.balance
            )
        }
    }
}

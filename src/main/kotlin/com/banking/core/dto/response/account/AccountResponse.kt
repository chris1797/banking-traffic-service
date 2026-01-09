package com.banking.core.dto.response.account

import com.banking.core.domain.AccountEntity
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
        fun from(accountEntity: AccountEntity): AccountResponse {
            return AccountResponse(
                id = accountEntity.id,
                status = accountEntity.status,
                createdAt = accountEntity.createdAt,
                updatedAt = accountEntity.updatedAt,
                accountNumber = accountEntity.accountNumber,
                holderName = accountEntity.holderName,
                balance = accountEntity.balance
            )
        }
    }
}

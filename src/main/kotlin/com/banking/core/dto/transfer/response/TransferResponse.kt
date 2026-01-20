package com.banking.core.dto.transfer.response

import com.banking.core.domain.AccountEntity
import com.banking.core.domain.TransferEntity
import com.banking.core.domain.TransferStatus
import java.math.BigDecimal
import java.time.LocalDateTime

data class TransferResponse(
    val id: Long,
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: BigDecimal,
    val transferStatus: TransferStatus,
    val failureReason: String?,
    val fromAccountBalance: BigDecimal?,
    val toAccountBalance: BigDecimal?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(
            transferEntity: TransferEntity,
            fromAccount: AccountEntity?,
            toAccount: AccountEntity?
        ): TransferResponse {
            return TransferResponse(
                id = transferEntity.id,
                fromAccountNumber = transferEntity.fromAccountNumber,
                toAccountNumber = transferEntity.toAccountNumber,
                amount = transferEntity.amount,
                transferStatus = transferEntity.transferStatus,
                failureReason = transferEntity.failureReason,
                fromAccountBalance = fromAccount?.balance,
                toAccountBalance = toAccount?.balance,
                createdAt = transferEntity.createdAt,
                updatedAt = transferEntity.updatedAt
            )
        }
    }
}

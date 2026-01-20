package com.banking.core.repository

import com.banking.core.domain.TransferEntity
import com.banking.core.domain.TransferStatus
import org.springframework.data.jpa.repository.JpaRepository

interface TransferRepository : JpaRepository<TransferEntity, Long> {

    fun findByFromAccountNumber(fromAccountNumber: String): List<TransferEntity>

    fun findByToAccountNumber(toAccountNumber: String): List<TransferEntity>

    fun findByFromAccountNumberOrToAccountNumber(
        fromAccountNumber: String,
        toAccountNumber: String
    ): List<TransferEntity>

    fun findByTransferStatus(transferStatus: TransferStatus): List<TransferEntity>
}
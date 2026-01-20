package com.banking.core.domain

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "transfer")
class TransferEntity(

    @Column(nullable = false)
    val fromAccountNumber: String,

    @Column(nullable = false)
    val toAccountNumber: String,

    @Column(nullable = false)
    val amount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var transferStatus: TransferStatus = TransferStatus.PENDING,

    @Column
    var failureReason: String? = null,

) : BaseEntity() {

    fun success() {
        require(transferStatus == TransferStatus.PENDING) { "PENDING 상태에서만 성공 처리 가능합니다." }
        this.transferStatus = TransferStatus.SUCCESS
    }

    fun fail(reason: String) {
        require(transferStatus == TransferStatus.PENDING) { "PENDING 상태에서만 실패 처리 가능합니다." }
        this.transferStatus = TransferStatus.FAILED
        this.failureReason = reason
    }

    companion object {
        fun create(
            fromAccountNumber: String,
            toAccountNumber: String,
            amount: BigDecimal
        ): TransferEntity {
            return TransferEntity(
                fromAccountNumber = fromAccountNumber,
                toAccountNumber = toAccountNumber,
                amount = amount,
                transferStatus = TransferStatus.PENDING
            )
        }
    }
}

enum class TransferStatus {
    PENDING,
    SUCCESS,
    FAILED
}
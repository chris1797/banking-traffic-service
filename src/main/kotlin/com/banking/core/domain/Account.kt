package com.banking.core.domain

import com.banking.core.support.response.error.CoreException
import com.banking.core.support.response.error.ErrorType
import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "account")
class Account(

    @Column(unique = true, nullable = false)
    val accountNumber: String,
    val holderName: String,
    var balance: BigDecimal,

    @Version
    val version: Long = 0,

    ) : BaseEntity() {

    internal fun deposit(amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "입금 금액은 0보다 커야 합니다." }
        this.balance += amount
    }

    internal fun withdraw(amount: BigDecimal) {
        require(amount > BigDecimal.ZERO) { "출금 금액은 0보다 커야 합니다." }
        require(this.balance >= amount) { "잔액이 부족합니다." }
        this.balance -= amount
    }

    companion object {
        fun create(
            accountNumber: String,
            holderName: String,
            balance: BigDecimal
        ): Account {
            return Account(
                accountNumber =  accountNumber,
                holderName = holderName,
                balance = balance
            )
        }
    }
}
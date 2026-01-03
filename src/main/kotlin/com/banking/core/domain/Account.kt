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

    fun deposit(amount: BigDecimal) {
        this.balance += amount
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
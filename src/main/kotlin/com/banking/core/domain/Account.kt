package com.banking.core.domain

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "account")
class Account(

    @Column(unique = true, nullable = false)
    val accountNumber: String,
    val holderName: String,
    val balance: BigDecimal,

) : BaseEntity() {

    companion object {
        fun create(
            accountNumber: String,
            holderName: String,
            balance: BigDecimal
        ): Account {
            require( holderName.isNotBlank() ) { "계좌주 이름은 비어 있을 수 없습니다." }
            return Account(
                accountNumber =  accountNumber,
                holderName = holderName,
                balance = balance
            )
        }
    }
}
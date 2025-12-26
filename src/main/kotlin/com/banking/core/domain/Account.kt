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

) : BaseEntity()
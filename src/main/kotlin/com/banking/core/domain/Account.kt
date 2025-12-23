package com.banking.core.domain

import jakarta.persistence.*
import java.math.BigDecimal

@Entity
@Table(name = "account")
open class Account(

    val accountNumber: String,
    val holderName: String,
    val balance: BigDecimal,

) : BaseEntity()
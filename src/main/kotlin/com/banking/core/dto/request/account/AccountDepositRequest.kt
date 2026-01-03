package com.banking.core.dto.request.account

import java.math.BigDecimal

data class AccountDepositRequest(
    val accountNumber: String,
    val amount: BigDecimal
)
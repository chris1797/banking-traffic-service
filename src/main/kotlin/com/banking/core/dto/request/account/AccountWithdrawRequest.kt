package com.banking.core.dto.request.account

import java.math.BigDecimal

data class AccountWithdrawRequest(
    val accountNumber: String,
    val amount: BigDecimal
)
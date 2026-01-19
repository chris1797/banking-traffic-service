package com.banking.core.dto.account.request

import java.math.BigDecimal

data class AccountWithdrawRequest(
    val accountNumber: String,
    val amount: BigDecimal
)
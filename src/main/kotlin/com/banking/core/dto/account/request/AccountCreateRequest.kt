package com.banking.core.dto.account.request

import java.math.BigDecimal

data class AccountCreateRequest(
    val holderName: String,
    val initialBalance: BigDecimal = BigDecimal.ZERO
)
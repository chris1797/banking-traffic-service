package com.banking.core.dto.request.account

import java.math.BigDecimal

data class AccountCreateRequest(
    val holderName: String,
    val initialBalance: BigDecimal = BigDecimal.ZERO
)
package com.banking.core.dto.transfer.request

import java.math.BigDecimal

data class TransferRequest(
    val fromAccountNumber: String,
    val toAccountNumber: String,
    val amount: BigDecimal
)

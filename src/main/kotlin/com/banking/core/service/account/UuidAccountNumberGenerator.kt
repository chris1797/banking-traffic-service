package com.banking.core.service.account

import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UuidAccountNumberGenerator : AccountNumberGenerator {

    override fun generate(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12)
    }
}
package com.banking.core.service

import com.banking.core.domain.Account
import com.banking.core.dto.request.AccountCreateRequest
import com.banking.core.repository.AccountRepository
import com.banking.core.support.response.error.CoreException
import com.banking.core.support.response.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AccountService(
    private val accountRepository: AccountRepository
) {

    companion object {
        private const val MAX_RETRY_COUNT = 3
    }

    @Transactional(rollbackFor = [Exception::class])
    fun createAccount(request: AccountCreateRequest): Account {
        repeat(MAX_RETRY_COUNT) {
            val accountNumber = generateAccountNumber()
            if (!accountRepository.existsByAccountNumber(accountNumber)) {
                val account = Account(
                    accountNumber = accountNumber,
                    holderName = request.holderName,
                    balance = request.initialBalance,
                )
                return accountRepository.save(account)
            }
        }
        throw CoreException(ErrorType.CREATE_ACCOUNT_FAILED)
    }

    /**
     * 랜덤한 12자리 계좌번호 생성
     */
    private fun generateAccountNumber(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12)
    }
}

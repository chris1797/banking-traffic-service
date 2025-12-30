package com.banking.core.service.account

import com.banking.core.domain.Account
import com.banking.core.domain.EntityStatus
import com.banking.core.dto.request.account.AccountCreateRequest
import com.banking.core.dto.response.account.AccountResponse
import com.banking.core.repository.AccountRepository
import com.banking.core.support.response.error.CoreException
import com.banking.core.support.response.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val accountNumberGenerator: AccountNumberGenerator,
    private val transactionTemplate: TransactionTemplate
) {

    private val log = LoggerFactory.getLogger(AccountService::class.java)

    companion object {
        private const val MAX_RETRY_COUNT = 3
    }

    fun createAccount(request: AccountCreateRequest): Account {
        var lastException: DataIntegrityViolationException? = null

        repeat(MAX_RETRY_COUNT) { attempt ->
            try {
                return transactionTemplate.execute {
                    val accountNumber = accountNumberGenerator.generate()
                    val account = Account.create(
                        accountNumber = accountNumber,
                        holderName = request.holderName,
                        balance = request.initialBalance,
                    )
                    accountRepository.save(account)
                }!!
            } catch (e: DataIntegrityViolationException) {
                lastException = e
                log.warn("계좌번호 충돌 발생, 재시도 중 (시도: ${attempt + 1}/$MAX_RETRY_COUNT)")
            }
        }
        throw CoreException(ErrorType.CREATE_ACCOUNT_FAILED).initCause(lastException)
    }

    @Transactional(readOnly = true)
    fun getAccount(accountNumber: String): AccountResponse {
        val account = accountRepository.findByAccountNumber(accountNumber)
            ?: throw CoreException(ErrorType.ACCOUNT_NOT_FOUND)

        if (account.isDeleted()) {
            throw CoreException(ErrorType.ACCOUNT_DELETED)
        }

        return AccountResponse.from(account)
    }
}

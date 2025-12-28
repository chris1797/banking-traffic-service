package com.banking.core.service

import com.banking.core.domain.Account
import com.banking.core.dto.request.AccountCreateRequest
import com.banking.core.repository.AccountRepository
import com.banking.core.service.account.AccountService
import com.banking.core.service.account.AccountNumberGenerator
import com.banking.core.support.response.error.CoreException
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

class AccountServiceTest {

    private lateinit var accountRepository: AccountRepository
    private lateinit var accountNumberGenerator: AccountNumberGenerator
    private lateinit var transactionTemplate: TransactionTemplate
    private lateinit var accountService: AccountService

    @BeforeEach
    fun setUp() {
        accountRepository = mockk()
        accountNumberGenerator = mockk()
        transactionTemplate = mockk()
        accountService = AccountService(
            accountRepository,
            accountNumberGenerator,
            transactionTemplate
        )
    }

    @Test
    fun `계좌 생성 성공`() {
        // given
        val request = AccountCreateRequest(
            holderName = "이재훈",
            initialBalance = BigDecimal(1000)
        )
        val expectedAccountNumber = "1234-5678-9012"

        every { accountNumberGenerator.generate() } returns expectedAccountNumber
        every { transactionTemplate.execute(any<TransactionCallback<Account>>())
        } answers {
            firstArg<TransactionCallback<Account>>().doInTransaction(mockk())
        }
        every { accountRepository.save(any()) } answers { firstArg() }

        // when
        val result = accountService.createAccount(request)

        // then
        assertThat(result.accountNumber).isEqualTo(expectedAccountNumber)
        assertThat(result.holderName).isEqualTo("이재훈")
        assertThat(result.balance).isEqualByComparingTo(BigDecimal(1000))

        verify(exactly = 1) { accountNumberGenerator.generate() }
        verify(exactly = 1) { accountRepository.save(any()) }
    }

    @Test
    fun `계좌번호 중복 발생 시 재시도하여 성공`() {
        // given
        val request = AccountCreateRequest(
            holderName = "이재훈",
            initialBalance = BigDecimal(1000)
        )

        every { accountNumberGenerator.generate() } returnsMany listOf(
            "duplicate00001",
            "unique12345678"
        )

        every { transactionTemplate.execute(any<TransactionCallback<Account>>()) } answers {
            firstArg<TransactionCallback<Account>>().doInTransaction(mockk())
        }

        var saveCallCount = 0
        every { accountRepository.save(any()) } answers {
            saveCallCount++
            if (saveCallCount == 1) {
                throw DataIntegrityViolationException("Duplicate entry")
            }
            firstArg()
        }

        // when
        val result = accountService.createAccount(request)

        // then
        assertThat(result.accountNumber).isEqualTo("unique12345678")
        assertThat(result.holderName).isEqualTo("이재훈")

        verify(exactly = 2) { accountNumberGenerator.generate() }
        verify(exactly = 2) { accountRepository.save(any()) }
    }

    @Test
    fun `최대 재시도 횟수 초과 시 예외 발생`() {
        // given
        val request = AccountCreateRequest(
            holderName = "이재훈",
            initialBalance = BigDecimal(1000)
        )

        every { accountNumberGenerator.generate() } returns "duplicate00001"
        every { transactionTemplate.execute(any<TransactionCallback<Account>>()) } answers {
            firstArg<TransactionCallback<Account>>().doInTransaction(mockk())
        }
        every { accountRepository.save(any()) } throws DataIntegrityViolationException("Duplicate entry")

        // when & then
        assertThatThrownBy { accountService.createAccount(request) }
            .isInstanceOf(CoreException::class.java)
            .hasCauseInstanceOf(DataIntegrityViolationException::class.java)

        verify(exactly = 3) { accountNumberGenerator.generate() }
        verify(exactly = 3) { accountRepository.save(any()) }
    }
}
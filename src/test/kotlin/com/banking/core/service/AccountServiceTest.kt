package com.banking.core.service

import com.banking.core.domain.Account
import com.banking.core.dto.request.account.AccountCreateRequest
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
    fun `계좌생성 전체 로직 성공 테스트`() {
        // given
        val request = AccountCreateRequest(
            holderName = "이재훈",
            initialBalance = BigDecimal(1000)
        )
        val expectedAccountNumber = "1234-5678-9012"

        // 생성된 계좌번호가 예상한 값과 일치하도록
        every { accountNumberGenerator.generate() } returns expectedAccountNumber

        // 트랜잭션 템플릿이 정상적으로 동작하도록 설정
        every { transactionTemplate.execute(any<TransactionCallback<Account>>()) } answers {
            firstArg<TransactionCallback<Account>>().doInTransaction(mockk())
        }

        // 저장소가 계좌를 정상적으로 저장하도록 설정
        every { accountRepository.save(any()) } answers { firstArg() }

        // when
        val result = accountService.createAccount(request)

        /*
        then
        1. 생성된 계좌의 속성이 예상한 값과 일치하는지 검증
        2. 계좌번호 생성기와 저장소가 각각 한 번씩 호출되었는지 검증
        3. 트랜잭션 템플릿이 한 번 호출되었는지 검증
         */
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

        // 첫 번째 호출에서는 중복된 계좌번호 반환, 두 번째 호출에서는 고유한 계좌번호 반환
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
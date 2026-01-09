package com.banking.core.service.account.unit

import com.banking.core.domain.AccountEntity
import com.banking.core.domain.EntityStatus
import com.banking.core.dto.request.account.AccountCreateRequest
import com.banking.core.dto.request.account.AccountDepositRequest
import com.banking.core.dto.response.account.AccountResponse
import com.banking.core.repository.AccountRepository
import com.banking.core.service.account.AccountNumberGenerator
import com.banking.core.service.account.AccountService
import com.banking.core.support.response.error.CoreException
import com.banking.core.support.response.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime

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
        every { transactionTemplate.execute(any<TransactionCallback<AccountResponse>>()) } answers {
            firstArg<TransactionCallback<AccountResponse>>().doInTransaction(mockk())
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
        Assertions.assertThat(result.accountNumber).isEqualTo(expectedAccountNumber)
        Assertions.assertThat(result.holderName).isEqualTo("이재훈")
        Assertions.assertThat(result.balance).isEqualByComparingTo(BigDecimal(1000))

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

        every { transactionTemplate.execute(any<TransactionCallback<AccountResponse>>()) } answers {
            firstArg<TransactionCallback<AccountResponse>>().doInTransaction(mockk())
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
        Assertions.assertThat(result.accountNumber).isEqualTo("unique12345678")
        Assertions.assertThat(result.holderName).isEqualTo("이재훈")

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
        every { transactionTemplate.execute(any<TransactionCallback<AccountEntity>>()) } answers {
            firstArg<TransactionCallback<AccountEntity>>().doInTransaction(mockk())
        }
        every { accountRepository.save(any()) } throws DataIntegrityViolationException("Duplicate entry")

        // when & then
        Assertions.assertThatThrownBy { accountService.createAccount(request) }
            .isInstanceOf(CoreException::class.java)
            .hasCauseInstanceOf(DataIntegrityViolationException::class.java)

        verify(exactly = 3) { accountNumberGenerator.generate() }
        verify(exactly = 3) { accountRepository.save(any()) }
    }

    @Nested
    inner class DepositTest {

        @Test
        fun `입금 성공 테스트`() {
            // given
            val accountNumber = "1234-5678-9012"
            val request = AccountDepositRequest(
                accountNumber = accountNumber,
                amount = BigDecimal(5000)
            )
            val accountEntity = mockk<AccountEntity>(relaxed = true)

            every { accountEntity.accountNumber } returns accountNumber
            every { accountEntity.holderName } returns "이재훈"
            every { accountEntity.balance } returns BigDecimal(6000)
            every { accountEntity.isActive() } returns true
            every { accountEntity.id } returns 1L
            every { accountEntity.status } returns EntityStatus.ACTIVE
            every { accountEntity.createdAt } returns LocalDateTime.now()
            every { accountEntity.updatedAt } returns LocalDateTime.now()

            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
                firstArg<TransactionCallback<*>>().doInTransaction(mockk())
            }
            every { accountRepository.findByAccountNumber(accountNumber) } returns accountEntity

            // when
            val result = accountService.deposit(request)

            // then
            Assertions.assertThat(result.accountNumber).isEqualTo(accountNumber)
            verify(exactly = 1) { accountEntity.deposit(BigDecimal(5000)) }
        }

        @Test
        fun `존재하지 않는 계좌에 입금 시 예외 발생`() {
            // given
            val request = AccountDepositRequest(
                accountNumber = "non-existent",
                amount = BigDecimal(5000)
            )

            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
                firstArg<TransactionCallback<*>>().doInTransaction(mockk())
            }
            every { accountRepository.findByAccountNumber("non-existent") } returns null

            // when & then
            Assertions.assertThatThrownBy { accountService.deposit(request) }
                .isInstanceOf(CoreException::class.java)
                .satisfies({ ex ->
                    Assertions.assertThat((ex as CoreException).errorType).isEqualTo(ErrorType.ACCOUNT_NOT_FOUND)
                })
        }

        @Test
        fun `삭제된 계좌에 입금 시 예외 발생`() {
            // given
            val accountNumber = "deleted-account"
            val request = AccountDepositRequest(
                accountNumber = accountNumber,
                amount = BigDecimal(5000)
            )
            val accountEntity = mockk<AccountEntity>(relaxed = true)

            every { accountEntity.isDeleted() } returns true
            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
                firstArg<TransactionCallback<*>>().doInTransaction(mockk())
            }
            every { accountRepository.findByAccountNumber(accountNumber) } returns accountEntity

            // when & then
            Assertions.assertThatThrownBy { accountService.deposit(request) }
                .isInstanceOf(CoreException::class.java)
                .satisfies({ ex ->
                    Assertions.assertThat((ex as CoreException).errorType).isEqualTo(ErrorType.ACCOUNT_DELETED)
                })
        }

        @Test
        fun `낙관적 락 충돌 발생 시 재시도하여 성공`() {
            // given
            val accountNumber = "1234-5678-9012"
            val request = AccountDepositRequest(
                accountNumber = accountNumber,
                amount = BigDecimal(5000)
            )
            val accountEntity = mockk<AccountEntity>(relaxed = true)

            every { accountEntity.accountNumber } returns accountNumber
            every { accountEntity.holderName } returns "이재훈"
            every { accountEntity.balance } returns BigDecimal(6000)
            every { accountEntity.isActive() } returns true
            every { accountEntity.id } returns 1L
            every { accountEntity.status } returns EntityStatus.ACTIVE
            every { accountEntity.createdAt } returns LocalDateTime.now()
            every { accountEntity.updatedAt } returns LocalDateTime.now()

            var callCount = 0
            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
                callCount++
                if (callCount == 1) {
                    throw ObjectOptimisticLockingFailureException(AccountEntity::class.java, "Optimistic lock conflict")
                }
                firstArg<TransactionCallback<*>>().doInTransaction(mockk())
            }
            every { accountRepository.findByAccountNumber(accountNumber) } returns accountEntity

            // when
            val result = accountService.deposit(request)

            // then
            Assertions.assertThat(result.accountNumber).isEqualTo(accountNumber)
            verify(exactly = 2) { transactionTemplate.execute(any<TransactionCallback<*>>()) }
        }

        @Test
        fun `낙관적 락 충돌 최대 재시도 횟수 초과 시 예외 발생`() {
            // given
            val request = AccountDepositRequest(
                accountNumber = "1234-5678-9012",
                amount = BigDecimal(5000)
            )

            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } throws
                    ObjectOptimisticLockingFailureException(AccountEntity::class.java, "Optimistic lock conflict")

            // when & then
            Assertions.assertThatThrownBy { accountService.deposit(request) }
                .isInstanceOf(CoreException::class.java)
                .satisfies({ ex ->
                    Assertions.assertThat((ex as CoreException).errorType).isEqualTo(ErrorType.DEPOSIT_FAILED)
                })
                .hasCauseInstanceOf(ObjectOptimisticLockingFailureException::class.java)

            verify(exactly = 3) { transactionTemplate.execute(any<TransactionCallback<*>>()) }
        }
    }
}
package com.banking.core.service.transfer.unit

import com.banking.core.domain.AccountEntity
import com.banking.core.domain.EntityStatus
import com.banking.core.domain.TransferEntity
import com.banking.core.domain.TransferStatus
import com.banking.core.dto.transfer.request.TransferRequest
import com.banking.core.repository.AccountRepository
import com.banking.core.repository.TransferRepository
import com.banking.core.service.transfer.TransferService
import com.banking.core.support.response.error.CoreException
import com.banking.core.support.response.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.*

class TransferServiceTest {

    private lateinit var transferRepository: TransferRepository
    private lateinit var accountRepository: AccountRepository
    private lateinit var transactionTemplate: TransactionTemplate
    private lateinit var transferService: TransferService

    @BeforeEach
    fun setUp() {
        transferRepository = mockk()
        accountRepository = mockk()
        transactionTemplate = mockk()
        transferService = TransferService(
            transferRepository,
            accountRepository,
            transactionTemplate
        )
    }

    @Nested
    inner class TransferTest {

        @Test
        fun `이체 성공 테스트`() {
            // given
            val fromAccountNumber = "1111-1111-1111"
            val toAccountNumber = "2222-2222-2222"
            val request = TransferRequest(
                fromAccountNumber = fromAccountNumber,
                toAccountNumber = toAccountNumber,
                amount = BigDecimal(5000)
            )

            val fromAccount = createMockAccount(fromAccountNumber, "출금자", BigDecimal(10000))
            val toAccount = createMockAccount(toAccountNumber, "입금자", BigDecimal(0))
            val transferEntity = createMockTransferEntity(1L, fromAccountNumber, toAccountNumber, BigDecimal(5000))

            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
                firstArg<TransactionCallback<*>>().doInTransaction(mockk())
            }
            every { transferRepository.save(any()) } returns transferEntity
            every { transferRepository.findById(1L) } returns Optional.of(transferEntity)
            every { accountRepository.findByAccountNumber(fromAccountNumber) } returns fromAccount
            every { accountRepository.findByAccountNumber(toAccountNumber) } returns toAccount

            // when
            val result = transferService.transfer(request)

            // then
            Assertions.assertThat(result.fromAccountNumber).isEqualTo(fromAccountNumber)
            Assertions.assertThat(result.toAccountNumber).isEqualTo(toAccountNumber)
            Assertions.assertThat(result.amount).isEqualByComparingTo(BigDecimal(5000))
            verify(exactly = 1) { fromAccount.withdraw(BigDecimal(5000)) }
            verify(exactly = 1) { toAccount.deposit(BigDecimal(5000)) }
        }

        @Test
        fun `출금 계좌가 존재하지 않을 때 예외 발생`() {
            // given
            val fromAccountNumber = "1111-1111-1111"
            val toAccountNumber = "2222-2222-2222"
            val request = TransferRequest(
                fromAccountNumber = fromAccountNumber,
                toAccountNumber = toAccountNumber,
                amount = BigDecimal(5000)
            )

            val transferEntity = createMockTransferEntity(1L, fromAccountNumber, toAccountNumber, BigDecimal(5000))

            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
                firstArg<TransactionCallback<*>>().doInTransaction(mockk())
            }
            every { transferRepository.save(any()) } returns transferEntity
            every { transferRepository.findById(1L) } returns Optional.of(transferEntity)
            // 정렬 순서: 1111 < 2222, 따라서 fromAccount가 먼저 조회됨
            every { accountRepository.findByAccountNumber(fromAccountNumber) } returns null

            // when & then
            Assertions.assertThatThrownBy { transferService.transfer(request) }
                .isInstanceOf(CoreException::class.java)
                .satisfies({ ex ->
                    Assertions.assertThat((ex as CoreException).errorType).isEqualTo(ErrorType.ACCOUNT_NOT_FOUND)
                })
        }

        @Test
        fun `입금 계좌가 존재하지 않을 때 예외 발생`() {
            // given
            val fromAccountNumber = "1111-1111-1111"
            val toAccountNumber = "non-existent"
            val request = TransferRequest(
                fromAccountNumber = fromAccountNumber,
                toAccountNumber = toAccountNumber,
                amount = BigDecimal(5000)
            )

            val fromAccount = createMockAccount(fromAccountNumber, "출금자", BigDecimal(10000))
            val transferEntity = createMockTransferEntity(1L, fromAccountNumber, toAccountNumber, BigDecimal(5000))

            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
                firstArg<TransactionCallback<*>>().doInTransaction(mockk())
            }
            every { transferRepository.save(any()) } returns transferEntity
            every { transferRepository.findById(1L) } returns Optional.of(transferEntity)
            every { accountRepository.findByAccountNumber(fromAccountNumber) } returns fromAccount
            every { accountRepository.findByAccountNumber(toAccountNumber) } returns null

            // when & then
            Assertions.assertThatThrownBy { transferService.transfer(request) }
                .isInstanceOf(CoreException::class.java)
                .satisfies({ ex ->
                    Assertions.assertThat((ex as CoreException).errorType).isEqualTo(ErrorType.ACCOUNT_NOT_FOUND)
                })
        }

        @Test
        fun `출금 계좌가 삭제된 경우 예외 발생`() {
            // given
            val fromAccountNumber = "1111-1111-1111"
            val toAccountNumber = "2222-2222-2222"
            val request = TransferRequest(
                fromAccountNumber = fromAccountNumber,
                toAccountNumber = toAccountNumber,
                amount = BigDecimal(5000)
            )

            val fromAccount = createMockAccount(fromAccountNumber, "출금자", BigDecimal(10000), isDeleted = true)
            val toAccount = createMockAccount(toAccountNumber, "입금자", BigDecimal(0))
            val transferEntity = createMockTransferEntity(1L, fromAccountNumber, toAccountNumber, BigDecimal(5000))

            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
                firstArg<TransactionCallback<*>>().doInTransaction(mockk())
            }
            every { transferRepository.save(any()) } returns transferEntity
            every { transferRepository.findById(1L) } returns Optional.of(transferEntity)
            every { accountRepository.findByAccountNumber(fromAccountNumber) } returns fromAccount
            every { accountRepository.findByAccountNumber(toAccountNumber) } returns toAccount

            // when & then
            Assertions.assertThatThrownBy { transferService.transfer(request) }
                .isInstanceOf(CoreException::class.java)
                .satisfies({ ex ->
                    Assertions.assertThat((ex as CoreException).errorType).isEqualTo(ErrorType.ACCOUNT_DELETED)
                })
        }

        @Test
        fun `입금 계좌가 삭제된 경우 예외 발생`() {
            // given
            val fromAccountNumber = "1111-1111-1111"
            val toAccountNumber = "2222-2222-2222"
            val request = TransferRequest(
                fromAccountNumber = fromAccountNumber,
                toAccountNumber = toAccountNumber,
                amount = BigDecimal(5000)
            )

            val fromAccount = createMockAccount(fromAccountNumber, "출금자", BigDecimal(10000))
            val toAccount = createMockAccount(toAccountNumber, "입금자", BigDecimal(0), isDeleted = true)
            val transferEntity = createMockTransferEntity(1L, fromAccountNumber, toAccountNumber, BigDecimal(5000))

            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
                firstArg<TransactionCallback<*>>().doInTransaction(mockk())
            }
            every { transferRepository.save(any()) } returns transferEntity
            every { transferRepository.findById(1L) } returns Optional.of(transferEntity)
            every { accountRepository.findByAccountNumber(fromAccountNumber) } returns fromAccount
            every { accountRepository.findByAccountNumber(toAccountNumber) } returns toAccount

            // when & then
            Assertions.assertThatThrownBy { transferService.transfer(request) }
                .isInstanceOf(CoreException::class.java)
                .satisfies({ ex ->
                    Assertions.assertThat((ex as CoreException).errorType).isEqualTo(ErrorType.ACCOUNT_DELETED)
                })
        }

        @Test
        fun `잔액 부족 시 예외 발생`() {
            // given
            val fromAccountNumber = "1111-1111-1111"
            val toAccountNumber = "2222-2222-2222"
            val request = TransferRequest(
                fromAccountNumber = fromAccountNumber,
                toAccountNumber = toAccountNumber,
                amount = BigDecimal(50000)
            )

            val fromAccount = createMockAccount(fromAccountNumber, "출금자", BigDecimal(10000))
            val toAccount = createMockAccount(toAccountNumber, "입금자", BigDecimal(0))
            val transferEntity = createMockTransferEntity(1L, fromAccountNumber, toAccountNumber, BigDecimal(50000))

            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
                firstArg<TransactionCallback<*>>().doInTransaction(mockk())
            }
            every { transferRepository.save(any()) } returns transferEntity
            every { transferRepository.findById(1L) } returns Optional.of(transferEntity)
            every { accountRepository.findByAccountNumber(fromAccountNumber) } returns fromAccount
            every { accountRepository.findByAccountNumber(toAccountNumber) } returns toAccount

            // when & then
            Assertions.assertThatThrownBy { transferService.transfer(request) }
                .isInstanceOf(CoreException::class.java)
                .satisfies({ ex ->
                    Assertions.assertThat((ex as CoreException).errorType).isEqualTo(ErrorType.INSUFFICIENT_BALANCE)
                })
        }

        @Test
        fun `동일 계좌로 이체 시 예외 발생`() {
            // given
            val accountNumber = "1111-1111-1111"
            val request = TransferRequest(
                fromAccountNumber = accountNumber,
                toAccountNumber = accountNumber,
                amount = BigDecimal(5000)
            )

            // when & then
            Assertions.assertThatThrownBy { transferService.transfer(request) }
                .isInstanceOf(CoreException::class.java)
                .satisfies({ ex ->
                    Assertions.assertThat((ex as CoreException).errorType).isEqualTo(ErrorType.SAME_ACCOUNT_TRANSFER)
                })
        }

        @Test
        fun `이체 금액이 0 이하일 때 예외 발생`() {
            // given
            val request = TransferRequest(
                fromAccountNumber = "1111-1111-1111",
                toAccountNumber = "2222-2222-2222",
                amount = BigDecimal.ZERO
            )

            // when & then
            Assertions.assertThatThrownBy { transferService.transfer(request) }
                .isInstanceOf(CoreException::class.java)
                .satisfies({ ex ->
                    Assertions.assertThat((ex as CoreException).errorType).isEqualTo(ErrorType.INVALID_TRANSFER_AMOUNT)
                })
        }

        @Test
        fun `낙관적 락 충돌 발생 시 재시도하여 성공`() {
            // given
            val fromAccountNumber = "1111-1111-1111"
            val toAccountNumber = "2222-2222-2222"
            val request = TransferRequest(
                fromAccountNumber = fromAccountNumber,
                toAccountNumber = toAccountNumber,
                amount = BigDecimal(5000)
            )

            val fromAccount = createMockAccount(fromAccountNumber, "출금자", BigDecimal(10000))
            val toAccount = createMockAccount(toAccountNumber, "입금자", BigDecimal(0))
            val transferEntity = createMockTransferEntity(1L, fromAccountNumber, toAccountNumber, BigDecimal(5000))

            var executeCallCount = 0
            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
                executeCallCount++
                // 첫 번째 호출은 PENDING 저장, 두 번째 호출은 충돌, 세 번째 호출은 성공
                if (executeCallCount == 2) {
                    throw ObjectOptimisticLockingFailureException(AccountEntity::class.java, "conflict")
                }
                firstArg<TransactionCallback<*>>().doInTransaction(mockk())
            }
            every { transferRepository.save(any()) } returns transferEntity
            every { transferRepository.findById(1L) } returns Optional.of(transferEntity)
            every { accountRepository.findByAccountNumber(fromAccountNumber) } returns fromAccount
            every { accountRepository.findByAccountNumber(toAccountNumber) } returns toAccount

            // when
            val result = transferService.transfer(request)

            // then
            Assertions.assertThat(result.fromAccountNumber).isEqualTo(fromAccountNumber)
            verify(exactly = 3) { transactionTemplate.execute(any<TransactionCallback<*>>()) }
        }

        @Test
        fun `낙관적 락 충돌 최대 재시도 횟수 초과 시 예외 발생`() {
            // given
            val fromAccountNumber = "1111-1111-1111"
            val toAccountNumber = "2222-2222-2222"
            val request = TransferRequest(
                fromAccountNumber = fromAccountNumber,
                toAccountNumber = toAccountNumber,
                amount = BigDecimal(5000)
            )

            val transferEntity = createMockTransferEntity(1L, fromAccountNumber, toAccountNumber, BigDecimal(5000))

            var executeCallCount = 0
            every { transactionTemplate.execute(any<TransactionCallback<*>>()) } answers {
                executeCallCount++
                // 첫 번째 호출은 PENDING 저장 성공, 이후 모두 충돌
                if (executeCallCount == 1) {
                    firstArg<TransactionCallback<*>>().doInTransaction(mockk())
                } else {
                    throw ObjectOptimisticLockingFailureException(AccountEntity::class.java, "conflict")
                }
            }
            every { transferRepository.save(any()) } returns transferEntity
            every { transferRepository.findById(1L) } returns Optional.of(transferEntity)

            // when & then
            Assertions.assertThatThrownBy { transferService.transfer(request) }
                .isInstanceOf(CoreException::class.java)
                .satisfies({ ex ->
                    Assertions.assertThat((ex as CoreException).errorType).isEqualTo(ErrorType.TRANSFER_FAILED)
                })
                .hasCauseInstanceOf(ObjectOptimisticLockingFailureException::class.java)
        }
    }

    private fun createMockAccount(
        accountNumber: String,
        holderName: String,
        balance: BigDecimal,
        isDeleted: Boolean = false
    ): AccountEntity {
        val account = mockk<AccountEntity>(relaxed = true)
        every { account.accountNumber } returns accountNumber
        every { account.holderName } returns holderName
        every { account.balance } returns balance
        every { account.isDeleted() } returns isDeleted
        every { account.isActive() } returns !isDeleted
        every { account.id } returns 1L
        every { account.status } returns if (isDeleted) EntityStatus.DELETED else EntityStatus.ACTIVE
        every { account.createdAt } returns LocalDateTime.now()
        every { account.updatedAt } returns LocalDateTime.now()
        return account
    }

    private fun createMockTransferEntity(
        id: Long,
        fromAccountNumber: String,
        toAccountNumber: String,
        amount: BigDecimal
    ): TransferEntity {
        val transfer = mockk<TransferEntity>(relaxed = true)
        every { transfer.id } returns id
        every { transfer.fromAccountNumber } returns fromAccountNumber
        every { transfer.toAccountNumber } returns toAccountNumber
        every { transfer.amount } returns amount
        every { transfer.transferStatus } returns TransferStatus.PENDING
        every { transfer.failureReason } returns null
        every { transfer.createdAt } returns LocalDateTime.now()
        every { transfer.updatedAt } returns LocalDateTime.now()
        return transfer
    }
}

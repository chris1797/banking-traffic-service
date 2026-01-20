package com.banking.core.service.transfer.integration

import com.banking.core.IntegrationTestSupport
import com.banking.core.dto.account.request.AccountCreateRequest
import com.banking.core.dto.transfer.request.TransferRequest
import com.banking.core.repository.AccountRepository
import com.banking.core.repository.TransferRepository
import com.banking.core.service.account.AccountService
import com.banking.core.service.transfer.TransferService
import com.banking.core.support.response.error.CoreException
import com.banking.core.support.response.error.ErrorType
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TransferServiceConcurrencyTest : IntegrationTestSupport() {

    @Autowired
    private lateinit var transferService: TransferService

    @Autowired
    private lateinit var accountService: AccountService

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var transferRepository: TransferRepository

    @BeforeEach
    fun setUp() {
        transferRepository.deleteAll()
        accountRepository.deleteAll()
    }

    @Test
    fun `동시에 10개 스레드에서 같은 계좌에서 이체 시 낙관적 락으로 정합성 보장`() {
        // given
        // 계좌 A: 10,000원, 계좌 B: 0원
        val accountA = accountService.createAccount(
            AccountCreateRequest(
                holderName = "A",
                initialBalance = BigDecimal(10000)
            )
        )
        val accountB = accountService.createAccount(
            AccountCreateRequest(
                holderName = "B",
                initialBalance = BigDecimal.ZERO
            )
        )

        val threadCount = 10
        val transferAmount = BigDecimal(1000)

        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when
        // 10개 스레드가 동시에 A -> B로 1,000원씩 이체
        repeat(threadCount) {
            executorService.submit {
                try {
                    transferService.transfer(
                        TransferRequest(
                            fromAccountNumber = accountA.accountNumber,
                            toAccountNumber = accountB.accountNumber,
                            amount = transferAmount
                        )
                    )
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        val finalAccountA = accountService.getAccount(accountA.accountNumber)
        val finalAccountB = accountService.getAccount(accountB.accountNumber)

        println("성공: ${successCount.get()}, 실패: ${failCount.get()}")
        println("A 최종 잔액: ${finalAccountA.balance}, B 최종 잔액: ${finalAccountB.balance}")

        // 10,000원에서 1,000원씩 10번 이체 → 모두 성공 시 A=0원, B=10,000원
        Assertions.assertThat(successCount.get()).isEqualTo(10)
        Assertions.assertThat(finalAccountA.balance).isEqualByComparingTo(BigDecimal.ZERO)
        Assertions.assertThat(finalAccountB.balance).isEqualByComparingTo(BigDecimal(10000))
    }

    @Test
    fun `동시에 여러 계좌에서 한 계좌로 이체 시 정합성 보장`() {
        // given
        // 계좌 A, B, C, D, E 각각 2,000원, 계좌 F 0원
        val accounts = (1..5).map { i ->
            accountService.createAccount(
                AccountCreateRequest(holderName = "Account$i", initialBalance = BigDecimal(2000))
            )
        }
        val targetAccount = accountService.createAccount(
            AccountCreateRequest(holderName = "Target", initialBalance = BigDecimal.ZERO)
        )

        val threadCount = 5
        val transferAmount = BigDecimal(2000)

        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when
        // 5개 스레드가 동시에 각 계좌 -> targetAccount로 2,000원 이체
        accounts.forEachIndexed { index, account ->
            executorService.submit {
                try {
                    transferService.transfer(
                        TransferRequest(
                            fromAccountNumber = account.accountNumber,
                            toAccountNumber = targetAccount.accountNumber,
                            amount = transferAmount
                        )
                    )
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    println("Thread $index failed: ${e.message}")
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        val finalTargetAccount = accountService.getAccount(targetAccount.accountNumber)
        val finalSourceBalances = accounts.map { accountService.getAccount(it.accountNumber).balance }

        println("성공: ${successCount.get()}, 실패: ${failCount.get()}")
        println("Target 최종 잔액: ${finalTargetAccount.balance}")
        println("Source 잔액들: $finalSourceBalances")

        // 모두 성공 시 A~E = 0원, Target = 10,000원
        Assertions.assertThat(successCount.get()).isEqualTo(5)
        Assertions.assertThat(finalTargetAccount.balance).isEqualByComparingTo(BigDecimal(10000))
        finalSourceBalances.forEach { balance ->
            Assertions.assertThat(balance).isEqualByComparingTo(BigDecimal.ZERO)
        }
    }

    @Test
    fun `잔액 부족 시 일부 이체만 성공`() {
        // given
        // 계좌 A: 5,000원, 계좌 B: 0원
        val accountA = accountService.createAccount(
            AccountCreateRequest(holderName = "A", initialBalance = BigDecimal(5000))
        )
        val accountB = accountService.createAccount(
            AccountCreateRequest(holderName = "B", initialBalance = BigDecimal.ZERO)
        )

        val threadCount = 10
        val transferAmount = BigDecimal(1000)

        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val insufficientBalanceCount = AtomicInteger(0)
        val otherFailCount = AtomicInteger(0)

        // when
        // 10개 스레드가 동시에 A -> B로 1,000원씩 이체 (5번만 성공 가능)
        repeat(threadCount) {
            executorService.submit {
                try {
                    transferService.transfer(
                        TransferRequest(
                            fromAccountNumber = accountA.accountNumber,
                            toAccountNumber = accountB.accountNumber,
                            amount = transferAmount
                        )
                    )
                    successCount.incrementAndGet()
                } catch (e: CoreException) {
                    if (e.errorType == ErrorType.INSUFFICIENT_BALANCE) {
                        insufficientBalanceCount.incrementAndGet()
                    } else {
                        otherFailCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    otherFailCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        val finalAccountA = accountService.getAccount(accountA.accountNumber)
        val finalAccountB = accountService.getAccount(accountB.accountNumber)

        println("성공: ${successCount.get()}, 잔액부족: ${insufficientBalanceCount.get()}, 기타실패: ${otherFailCount.get()}")
        println("A 최종 잔액: ${finalAccountA.balance}, B 최종 잔액: ${finalAccountB.balance}")

        // 5,000원에서 1,000원씩 -> 최대 5번만 성공 가능
        Assertions.assertThat(successCount.get()).isEqualTo(5)
        Assertions.assertThat(insufficientBalanceCount.get()).isEqualTo(5)
        Assertions.assertThat(finalAccountA.balance).isEqualByComparingTo(BigDecimal.ZERO)
        Assertions.assertThat(finalAccountB.balance).isEqualByComparingTo(BigDecimal(5000))
    }

    @Test
    fun `교차 이체 시 Deadlock 없이 처리`() {
        // given
        // 계좌 A: 5,000원, 계좌 B: 5,000원
        val accountA = accountService.createAccount(
            AccountCreateRequest(holderName = "A", initialBalance = BigDecimal(5000))
        )
        val accountB = accountService.createAccount(
            AccountCreateRequest(holderName = "B", initialBalance = BigDecimal(5000))
        )

        val threadCount = 10
        val transferAmount = BigDecimal(1000)

        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when
        // 스레드 1~5: A -> B 1,000원
        // 스레드 6~10: B -> A 1,000원
        repeat(threadCount) { index ->
            executorService.submit {
                try {
                    if (index < 5) {
                        transferService.transfer(
                            TransferRequest(
                                fromAccountNumber = accountA.accountNumber,
                                toAccountNumber = accountB.accountNumber,
                                amount = transferAmount
                            )
                        )
                    } else {
                        transferService.transfer(
                            TransferRequest(
                                fromAccountNumber = accountB.accountNumber,
                                toAccountNumber = accountA.accountNumber,
                                amount = transferAmount
                            )
                        )
                    }
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    println("Thread $index failed: ${e.message}")
                    failCount.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        executorService.shutdown()

        // then
        val finalAccountA = accountService.getAccount(accountA.accountNumber)
        val finalAccountB = accountService.getAccount(accountB.accountNumber)

        println("성공: ${successCount.get()}, 실패: ${failCount.get()}")
        println("A 최종 잔액: ${finalAccountA.balance}, B 최종 잔액: ${finalAccountB.balance}")

        // 모두 성공 시: A -> B 5,000원, B -> A 5,000원 → A = 5,000원, B = 5,000원
        Assertions.assertThat(successCount.get()).isEqualTo(10)
        Assertions.assertThat(finalAccountA.balance).isEqualByComparingTo(BigDecimal(5000))
        Assertions.assertThat(finalAccountB.balance).isEqualByComparingTo(BigDecimal(5000))
    }
}

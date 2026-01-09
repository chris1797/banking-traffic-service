package com.banking.core.service.account.integration

import com.banking.core.IntegrationTestSupport
import com.banking.core.dto.request.account.AccountCreateRequest
import com.banking.core.dto.request.account.AccountWithdrawRequest
import com.banking.core.repository.AccountRepository
import com.banking.core.service.account.AccountService
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

class AccountServiceConcurrencyTest : IntegrationTestSupport() {

    @Autowired
    private lateinit var accountService: AccountService

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @BeforeEach
    fun setUp() {
        accountRepository.deleteAll()
    }

    @Test
    fun `동시에 10개 스레드에서 출금 시 낙관적 락으로 정합성 보장`() {
        // given
        val initialBalance = BigDecimal(10000)
        val withdrawAmount = BigDecimal(1000)
        val threadCount = 10

        val account = accountService.createAccount(
            AccountCreateRequest(
                holderName = "테스트",
                initialBalance = initialBalance
            )
        )

        // ExecutorService: 10개의 스레드를 가진 스레드풀 생성
        val executorService = Executors.newFixedThreadPool(threadCount)
        // CountDownLatch: 모든 스레드가 작업을 완료할 때까지 메인 스레드 대기
        val latch = CountDownLatch(threadCount)
        // AtomicInteger: 멀티스레드 환경에서 안전한 카운터 (동시 접근 시 정합성 보장)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when
        // 10개의 스레드가 동시에 출금 요청을 실행
        repeat(threadCount) {
            // 스레드풀에 작업 제출 (비동기 실행)
            executorService.submit {
                try {
                    accountService.withdraw(
                        AccountWithdrawRequest(
                            accountNumber = account.accountNumber,
                            amount = withdrawAmount
                        )
                    )
                    successCount.incrementAndGet()
                } catch (e: Exception) {
                    failCount.incrementAndGet()
                } finally {
                    // 작업 완료 시 카운트 감소 (0이 되면 await() 해제)
                    latch.countDown()
                }
            }
        }

        // 모든 스레드의 작업이 완료될 때까지 대기
        latch.await()
        // 스레드풀 종료
        executorService.shutdown()

        // then
        val finalAccount = accountService.getAccount(account.accountNumber)

        println("성공: ${successCount.get()}, 실패: ${failCount.get()}")
        println("최종 잔액: ${finalAccount.balance}")

        // 10,000원에서 1,000원씩 10번 출금 → 모두 성공 시 잔액 0원
        Assertions.assertThat(successCount.get()).isEqualTo(10)
        Assertions.assertThat(finalAccount.balance).isEqualByComparingTo(BigDecimal.ZERO)
    }

    @Test
    fun `잔액 부족 시 일부 출금만 성공`() {
        // given
        val initialBalance = BigDecimal(5000)
        val withdrawAmount = BigDecimal(1000)
        val threadCount = 10

        val account = accountService.createAccount(
            AccountCreateRequest(
                holderName = "테스트",
                initialBalance = initialBalance
            )
        )

        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val insufficientBalanceCount = AtomicInteger(0)
        val otherFailCount = AtomicInteger(0)

        // when
        // 10개의 스레드가 동시에 출금 요청을 실행
        repeat(threadCount) {
            // 스레드풀에 작업 제출 (비동기 실행)
            executorService.submit {
                try {
                    accountService.withdraw(
                        AccountWithdrawRequest(
                            accountNumber = account.accountNumber,
                            amount = withdrawAmount
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
        val finalAccount = accountService.getAccount(account.accountNumber)

        println("성공: ${successCount.get()}, 잔액부족: ${insufficientBalanceCount.get()}, 기타실패: ${otherFailCount.get()}")
        println("최종 잔액: ${finalAccount.balance}")

        // 5,000원에서 1,000원씩 -> 최대 5번만 성공 가능
        Assertions.assertThat(successCount.get()).isEqualTo(5)
        Assertions.assertThat(insufficientBalanceCount.get()).isEqualTo(5)
        Assertions.assertThat(finalAccount.balance).isEqualByComparingTo(BigDecimal.ZERO)
    }
}
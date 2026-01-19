# 동시성 테스트 템플릿

## 스레드풀을 이용한 동시성 테스트

### 기본 구조

```kotlin
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Test
fun `동시성 테스트 템플릿`() {
    // given
    val threadCount = 10  // 동시 실행할 스레드 수

    // ExecutorService: 스레드풀 생성
    val executorService = Executors.newFixedThreadPool(threadCount)

    // CountDownLatch: 모든 스레드 완료 대기용
    val latch = CountDownLatch(threadCount)

    // AtomicInteger: 스레드 안전한 카운터
    val successCount = AtomicInteger(0)
    val failCount = AtomicInteger(0)

    // when
    repeat(threadCount) {
        executorService.submit {
            try {
                // 동시 실행할 작업
                doSomething()
                successCount.incrementAndGet()
            } catch (e: Exception) {
                failCount.incrementAndGet()
            } finally {
                latch.countDown()  // 작업 완료 시 카운트 감소
            }
        }
    }

    latch.await()  // 모든 스레드 완료 대기
    executorService.shutdown()  // 스레드풀 종료

    // then
    Assertions.assertThat(successCount.get()).isEqualTo(expectedSuccessCount)
    Assertions.assertThat(failCount.get()).isEqualTo(expectedFailCount)
}
```

---

### 핵심 컴포넌트 설명

| 컴포넌트 | 역할 |
|---------|------|
| `ExecutorService` | 스레드풀 관리, 작업 제출 |
| `CountDownLatch` | 모든 스레드 완료까지 메인 스레드 대기 |
| `AtomicInteger` | 멀티스레드 환경에서 안전한 카운터 |

---

### 예외 타입별 분류 템플릿

```kotlin
@Test
fun `예외 타입별 분류 템플릿`() {
    // given
    val threadCount = 10
    val executorService = Executors.newFixedThreadPool(threadCount)
    val latch = CountDownLatch(threadCount)

    val successCount = AtomicInteger(0)
    val insufficientBalanceCount = AtomicInteger(0)
    val otherFailCount = AtomicInteger(0)

    // when
    repeat(threadCount) {
        executorService.submit {
            try {
                doSomething()
                successCount.incrementAndGet()
            } catch (e: CoreException) {
                // 비즈니스 예외 분류
                when (e.errorType) {
                    ErrorType.INSUFFICIENT_BALANCE -> insufficientBalanceCount.incrementAndGet()
                    else -> otherFailCount.incrementAndGet()
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
    println("성공: ${successCount.get()}")
    println("잔액부족: ${insufficientBalanceCount.get()}")
    println("기타실패: ${otherFailCount.get()}")

    Assertions.assertThat(successCount.get()).isEqualTo(5)
    Assertions.assertThat(insufficientBalanceCount.get()).isEqualTo(5)
}
```

---

### 인덱스별 다른 작업 실행 템플릿

```kotlin
@Test
fun `인덱스별 다른 작업 실행 (교차 이체 등)`() {
    // given
    val threadCount = 10
    val executorService = Executors.newFixedThreadPool(threadCount)
    val latch = CountDownLatch(threadCount)
    val successCount = AtomicInteger(0)
    val failCount = AtomicInteger(0)

    // when
    repeat(threadCount) { index ->
        executorService.submit {
            try {
                if (index < 5) {
                    // 스레드 0~4: 작업 A
                    doTaskA()
                } else {
                    // 스레드 5~9: 작업 B
                    doTaskB()
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
    Assertions.assertThat(successCount.get()).isEqualTo(10)
}
```

---

### 여러 리소스 동시 접근 템플릿

```kotlin
@Test
fun `여러 리소스에서 한 리소스로 동시 접근`() {
    // given
    val sources = (1..5).map { createSource(it) }
    val target = createTarget()

    val threadCount = sources.size
    val executorService = Executors.newFixedThreadPool(threadCount)
    val latch = CountDownLatch(threadCount)
    val successCount = AtomicInteger(0)
    val failCount = AtomicInteger(0)

    // when
    sources.forEachIndexed { index, source ->
        executorService.submit {
            try {
                transferFromSourceToTarget(source, target)
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
    Assertions.assertThat(successCount.get()).isEqualTo(5)
}
```

---

### 타임아웃 설정 템플릿

```kotlin
import java.util.concurrent.TimeUnit

@Test
fun `타임아웃이 있는 동시성 테스트`() {
    // given
    val threadCount = 10
    val executorService = Executors.newFixedThreadPool(threadCount)
    val latch = CountDownLatch(threadCount)
    val successCount = AtomicInteger(0)

    // when
    repeat(threadCount) {
        executorService.submit {
            try {
                doSomething()
                successCount.incrementAndGet()
            } finally {
                latch.countDown()
            }
        }
    }

    // 최대 30초 대기 (타임아웃 설정)
    val completed = latch.await(30, TimeUnit.SECONDS)
    executorService.shutdown()

    // then
    Assertions.assertThat(completed).isTrue()  // 타임아웃 없이 완료됨
    Assertions.assertThat(successCount.get()).isEqualTo(10)
}
```

---

### 실제 사용 예시 (이체 동시성 테스트)

```kotlin
class TransferServiceConcurrencyTest : IntegrationTestSupport() {

    @Autowired
    private lateinit var transferService: TransferService

    @Autowired
    private lateinit var accountService: AccountService

    @Test
    fun `동시에 10개 스레드에서 같은 계좌에서 이체 시 낙관적 락으로 정합성 보장`() {
        // given: 계좌 A(10,000원) → 계좌 B(0원)
        val accountA = accountService.createAccount(
            AccountCreateRequest(holderName = "A", initialBalance = BigDecimal(10000))
        )
        val accountB = accountService.createAccount(
            AccountCreateRequest(holderName = "B", initialBalance = BigDecimal.ZERO)
        )

        val threadCount = 10
        val transferAmount = BigDecimal(1000)

        val executorService = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val successCount = AtomicInteger(0)
        val failCount = AtomicInteger(0)

        // when: 10개 스레드가 동시에 A → B로 1,000원씩 이체
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

        // then: 모두 성공, A=0원, B=10,000원
        val finalA = accountService.getAccount(accountA.accountNumber)
        val finalB = accountService.getAccount(accountB.accountNumber)

        Assertions.assertThat(successCount.get()).isEqualTo(10)
        Assertions.assertThat(finalA.balance).isEqualByComparingTo(BigDecimal.ZERO)
        Assertions.assertThat(finalB.balance).isEqualByComparingTo(BigDecimal(10000))
    }
}
```

---

## 주의사항

1. **테스트 격리**: `@BeforeEach`에서 데이터 초기화 필수
2. **스레드풀 종료**: `executorService.shutdown()` 호출 필수
3. **타임아웃**: 무한 대기 방지를 위해 `latch.await(timeout, unit)` 권장
4. **로깅**: 디버깅을 위해 실패 시 메시지 출력 권장
5. **TestContainer**: 통합 테스트 시 실제 DB 사용 권장 (H2는 동시성 테스트에 부적합)

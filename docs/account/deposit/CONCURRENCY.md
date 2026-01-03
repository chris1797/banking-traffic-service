# 계좌(Account) 도메인 동시성 이슈

## 입금 (deposit)

### 1. Lost Update (유실된 업데이트)

**문제점:**
- 현재 `findByAccountNumber`는 일반 SELECT로 조회하여 락이 없음
- 두 요청이 동시에 같은 계좌에 입금할 경우 한쪽 업데이트가 유실됨

**시나리오:**
```
시간  요청A (500원 입금)          요청B (300원 입금)
─────────────────────────────────────────────────────
T1    SELECT balance = 1000
T2                                SELECT balance = 1000
T3    balance = 1000 + 500 = 1500
T4    UPDATE balance = 1500
T5                                balance = 1000 + 300 = 1300
T6                                UPDATE balance = 1300  ← A의 입금 유실!
```

**이전 코드:**
```kotlin
// AccountService.kt (문제 있던 코드)
@Transactional
fun deposit(accountNumber: String, amount: BigDecimal) {
    val accountEntity = accountRepository.findByAccountNumber(accountNumber)  // 락 없음
    accountEntity.deposit(amount)  // 충돌 시 Lost Update 발생
}
```

**해결 방안:**

#### 방안 1: 비관적 락 (Pessimistic Lock)
```kotlin
// AccountRepository.kt
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
fun findByAccountNumberForUpdate(accountNumber: String): Account?
```
- 장점: 충돌이 잦은 경우 효율적, 구현 간단
- 단점: DB 락으로 인한 대기 시간 발생, 데드락 가능성

#### 방안 2: 낙관적 락 (Optimistic Lock)
```kotlin
// Account.kt
@Version
var version: Long = 0
```
- 장점: 충돌이 적은 경우 성능 우수, DB 락 대기 없음
- 단점: 충돌 시 재시도 로직 필요

### 락 전략 선택: 낙관적 락 (Optimistic Lock)

**판단 근거:**

| 고려 사항 | 분석 |
|-----------|------|
| 계좌 유형 | 개인 계좌 (단일 소유자) |
| 동시 접근 빈도 | 낮음 - 본인 또는 단일 소스(급여, 이체)에서 입금 |
| 충돌 확률 | 낮음 - 한 계좌에 동시 입금 요청 발생 확률 낮음 |
| 성능 요구사항 | DB 락 대기 없이 빠른 응답 필요 |

**낙관적 락 선택 이유:**
1. **개인 계좌 특성**: 한 사람이 소유한 계좌에 동시에 여러 입금이 발생할 가능성이 낮음
2. **성능 우선**: 충돌이 드물기 때문에 락 대기 시간 없이 처리 가능
3. **확장성**: DB 락을 사용하지 않아 다중 인스턴스 환경에서도 유리

**비관적 락이 적합한 케이스 (참고):**
- 법인/공동 계좌: 여러 담당자가 동시 접근
- 인기 판매자 계좌: 동시에 많은 결제 수신
- 이벤트/모금 계좌: 단기간 대량 입금

**적용된 구현:**
```kotlin
// Account.kt - 낙관적 락
@Version
val version: Long = 0

// AccountService.kt - TransactionTemplate + 재시도 로직
fun deposit(request: AccountDepositRequest): AccountResponse {
    require(request.amount > BigDecimal.ZERO) { "입금 금액은 0보다 커야 합니다." }

    var lastException: ObjectOptimisticLockingFailureException? = null

    repeat(MAX_RETRY_COUNT) { attempt ->
        try {
            return transactionTemplate.execute {
                val accountEntity = accountRepository.findByAccountNumber(request.accountNumber)
                    ?: throw CoreException(ErrorType.ACCOUNT_NOT_FOUND)

                if (!accountEntity.isActive()) {
                    throw CoreException(ErrorType.ACCOUNT_DELETED)
                }

                accountEntity.deposit(request.amount)
                AccountResponse.from(accountEntity)
            }!!
        } catch (e: ObjectOptimisticLockingFailureException) {
            lastException = e
            log.warn("입금 충돌 발생, 재시도 중 (계좌: ${request.accountNumber}, 시도: ${attempt + 1}/$MAX_RETRY_COUNT)")
        }
    }
    throw CoreException(ErrorType.DEPOSIT_FAILED).initCause(lastException)
}
```

**주요 설계 결정:**
- `TransactionTemplate`: 재시도마다 새 트랜잭션 시작 (self-invocation 문제 회피)
- `MAX_RETRY_COUNT = 3`: createAccount와 동일한 재시도 횟수
- `DEPOSIT_FAILED`: 재시도 실패 시 클라이언트에 409 Conflict 응답

**상태:** 해결됨

---

### 2. Double Submit (중복 요청)

**문제점:**
- 네트워크 지연이나 사용자의 더블클릭으로 동일 입금 요청이 중복 처리될 수 있음
- 멱등성(Idempotency)이 보장되지 않음

**시나리오:**
```
사용자가 10,000원 입금 버튼을 빠르게 두 번 클릭
→ 요청 A: 10,000원 입금 성공
→ 요청 B: 10,000원 입금 성공 (중복!)
→ 결과: 20,000원 입금됨
```

**해결 방안:**

#### 방안 1: Idempotency Key
```kotlin
// Controller
@PostMapping("/v1/account/{accountNumber}/deposit")
fun deposit(
    @PathVariable accountNumber: String,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody request: AccountDepositRequest
): ApiResponse<Any>

// Service
fun deposit(accountNumber: String, amount: BigDecimal, idempotencyKey: String) {
    // Redis 또는 DB에서 idempotencyKey 중복 체크
    if (idempotencyKeyStore.exists(idempotencyKey)) {
        return // 이미 처리된 요청
    }
    // 입금 처리
    idempotencyKeyStore.save(idempotencyKey, TTL = 24h)
}
```

#### 방안 2: 트랜잭션 ID 기반 중복 체크
- 각 입금에 고유 트랜잭션 ID 부여
- 트랜잭션 테이블에서 중복 체크

**권장:** Idempotency Key 방식 (클라이언트 제어 가능)

**상태:** 미해결

---

### 3. 도메인 검증 부재

**문제점:**
- `Account.deposit()` 메서드에서 금액 검증이 없음
- 서비스 외 다른 곳에서 호출 시 무결성이 깨질 수 있음

**현재 코드:**
```kotlin
// Account.kt
fun deposit(amount: BigDecimal) {
    this.balance += amount  // amount 검증 없음
}
```

**해결 방안:**
```kotlin
// Account.kt
fun deposit(amount: BigDecimal) {
    require(amount > BigDecimal.ZERO) { "입금 금액은 0보다 커야 합니다." }
    this.balance += amount
}
```

**상태:** 미해결

---

## 우선순위 요약

| 문제 | 우선순위 | 해결책 | 상태 |
|------|---------|--------|------|
| Lost Update | 높음 | 낙관적 락 (@Version) + TransactionTemplate 재시도 | 해결됨 |
| Double Submit | 중간 | Idempotency Key | 미해결 |
| 도메인 검증 부재 | 낮음 | Account.deposit()에 require 추가 | 미해결 |
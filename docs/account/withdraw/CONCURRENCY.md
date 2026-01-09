# 계좌(Account) 도메인 동시성 이슈

## 출금 (withdraw)

### 1. Lost Update (유실된 업데이트)

**문제점:**
- 현재 `findByAccountNumber`는 일반 SELECT로 조회하여 락이 없음
- 두 요청이 동시에 같은 계좌에서 출금할 경우 한쪽 업데이트가 유실됨

**시나리오:**
```
시간  요청A (500원 출금)          요청B (300원 출금)
─────────────────────────────────────────────────────
T1    SELECT balance = 1000
T2                                SELECT balance = 1000
T3    balance = 1000 - 500 = 500
T4    UPDATE balance = 500
T5                                balance = 1000 - 300 = 700
T6                                UPDATE balance = 700  <- A의 출금 유실!
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
| 동시 접근 빈도 | 낮음 - 본인만 출금 가능 |
| 충돌 확률 | 낮음 - 한 계좌에 동시 출금 요청 발생 확률 낮음 |
| 성능 요구사항 | DB 락 대기 없이 빠른 응답 필요 |

**낙관적 락 선택 이유:**
1. **개인 계좌 특성**: 한 사람이 소유한 계좌에 동시에 여러 출금이 발생할 가능성이 낮음
2. **성능 우선**: 충돌이 드물기 때문에 락 대기 시간 없이 처리 가능
3. **확장성**: DB 락을 사용하지 않아 다중 인스턴스 환경에서도 유리

**비관적 락이 적합한 케이스 (참고):**
- 법인/공동 계좌: 여러 담당자가 동시 접근
- 자동 결제 계좌: 여러 정기 결제가 동시 실행
- 이벤트/프로모션: 단기간 대량 출금 요청

**적용된 구현:**
```kotlin
// Account.kt - 낙관적 락
@Version
val version: Long = 0

// AccountService.kt - TransactionTemplate + 재시도 로직
fun withdraw(request: AccountWithdrawRequest): AccountResponse {
    var lastException: Exception? = null

    repeat(MAX_RETRY_COUNT) { attempt ->
        try {
            return transactionTemplate.execute {
                val accountEntity = accountRepository.findByAccountNumber(request.accountNumber)
                    ?: throw CoreException(ErrorType.ACCOUNT_NOT_FOUND)

                if (accountEntity.isDeleted()) {
                    throw CoreException(ErrorType.ACCOUNT_DELETED)
                }

                if (accountEntity.balance < request.amount) {
                    throw CoreException(ErrorType.INSUFFICIENT_BALANCE)
                }

                accountEntity.withdraw(request.amount)
                AccountResponse.from(accountEntity)
            } ?: throw CoreException(ErrorType.WITHDRAW_FAILED)
        } catch (e: ObjectOptimisticLockingFailureException) {
            lastException = e
            log.warn("출금 충돌 발생, 재시도 중 (계좌: ${request.accountNumber}, 시도: ${attempt + 1}/$MAX_RETRY_COUNT)")
        }
    }
    throw CoreException(ErrorType.WITHDRAW_FAILED).initCause(lastException)
}
```

**주요 설계 결정:**
- `TransactionTemplate`: 재시도마다 새 트랜잭션 시작 (self-invocation 문제 회피)
- `MAX_RETRY_COUNT = 10`: 동시성 테스트에서 10개 스레드 동시 요청 처리를 위해 설정
- `WITHDRAW_FAILED`: 재시도 실패 시 클라이언트에 409 Conflict 응답
- `INSUFFICIENT_BALANCE`: 잔액 부족 시 400 Bad Request 응답

**상태:** 해결됨

---

### 2. 잔액 부족 검사의 Race Condition

**문제점:**
- 잔액 체크와 출금 사이에 다른 트랜잭션이 출금을 완료할 수 있음
- 낙관적 락 없이는 잔액이 음수가 될 수 있음

**시나리오:**
```
잔액: 1000원

시간  요청A (800원 출금)          요청B (800원 출금)
─────────────────────────────────────────────────────
T1    SELECT balance = 1000
T2    IF 1000 >= 800 -> OK
T3                                SELECT balance = 1000
T4                                IF 1000 >= 800 -> OK
T5    UPDATE balance = 200
T6                                UPDATE balance = 200  <- 잔액 200인데 800 출금!
```

**해결:**
- 낙관적 락(@Version)으로 T6에서 충돌 감지
- 재시도 시 최신 잔액(200원)으로 다시 검사하여 INSUFFICIENT_BALANCE 반환

**상태:** 해결됨 (낙관적 락으로 해결)

---

### 3. Double Submit (중복 요청)

**문제점:**
- 네트워크 지연이나 사용자의 더블클릭으로 동일 출금 요청이 중복 처리될 수 있음
- 멱등성(Idempotency)이 보장되지 않음

**시나리오:**
```
사용자가 10,000원 출금 버튼을 빠르게 두 번 클릭
-> 요청 A: 10,000원 출금 성공
-> 요청 B: 10,000원 출금 성공 (중복!)
-> 결과: 20,000원 출금됨
```

**해결 방안:**

#### 방안 1: Idempotency Key
```kotlin
// Controller
@PatchMapping("/v1/account/withdraw")
fun withdraw(
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody request: AccountWithdrawRequest
): ApiResponse<Any>

// Service
fun withdraw(request: AccountWithdrawRequest, idempotencyKey: String) {
    // Redis 또는 DB에서 idempotencyKey 중복 체크
    if (idempotencyKeyStore.exists(idempotencyKey)) {
        return // 이미 처리된 요청
    }
    // 출금 처리
    idempotencyKeyStore.save(idempotencyKey, TTL = 24h)
}
```

#### 방안 2: 트랜잭션 ID 기반 중복 체크
- 각 출금에 고유 트랜잭션 ID 부여
- 트랜잭션 테이블에서 중복 체크

**권장:** Idempotency Key 방식 (클라이언트 제어 가능)

**상태:** 미해결

---

### 4. 도메인 검증

**현재 구현:**
```kotlin
// Account.kt
internal fun withdraw(amount: BigDecimal) {
    require(amount > BigDecimal.ZERO) { "출금 금액은 0보다 커야 합니다." }
    require(this.balance >= amount) { "잔액이 부족합니다." }
    this.balance -= amount
}
```

**설계 결정:**
- `internal`: 같은 모듈 내에서만 호출 가능 (외부 모듈에서 무분별한 호출 방지)
- `require`: 금액 및 잔액 검증 (도메인 무결성 보장)

**상태:** 해결됨

---

## 동시성 테스트

### 테스트 환경
- Testcontainers + PostgreSQL 15
- 10개 스레드 동시 실행

### 테스트 케이스

#### 케이스 1: 동시에 10개 스레드에서 출금
```
초기 잔액: 10,000원
출금 금액: 1,000원 x 10회
기대 결과: 모두 성공, 최종 잔액 0원
```

#### 케이스 2: 잔액 부족 시 일부만 성공
```
초기 잔액: 5,000원
출금 금액: 1,000원 x 10회
기대 결과: 5회 성공, 5회 잔액 부족, 최종 잔액 0원
```

### 재시도 횟수 튜닝

| MAX_RETRY_COUNT | 동시 요청 10개 결과 |
|-----------------|-------------------|
| 3 | 5/10 성공 (재시도 초과) |
| 10 | 10/10 성공 |

**결론:** 동시 요청 수에 따라 재시도 횟수 조정 필요

---

## 우선순위 요약

| 문제 | 우선순위 | 해결책 | 상태 |
|------|---------|--------|------|
| Lost Update | 높음 | 낙관적 락 (@Version) + TransactionTemplate 재시도 | 해결됨 |
| 잔액 부족 Race Condition | 높음 | 낙관적 락으로 충돌 감지 후 재검사 | 해결됨 |
| Double Submit | 중간 | Idempotency Key | 미해결 |
| 도메인 검증 | 낮음 | internal + require 적용 | 해결됨 |
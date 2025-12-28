# 계좌(Account) 도메인 동시성 이슈

## 계좌 생성 (createAccount)

### 1. 계좌번호 중복 가능성

**문제점:**
- UUID를 12자로 자르면 충돌 확률이 급격히 증가
- 동시 요청 시 중복 계좌번호 생성 가능

**해결 방안:**
- DB에 `accountNumber` unique 제약조건 추가
- TransactionTemplate을 사용하여 각 재시도마다 독립적인 트랜잭션 생성
- 최대 3회 재시도 로직 구현

**적용된 구현:**
```kotlin
// Account.kt - unique 제약조건
@Column(unique = true, nullable = false)
val accountNumber: String

// AccountService.kt - TransactionTemplate + 재시도 로직
fun createAccount(request: AccountCreateRequest): Account {
    var lastException: DataIntegrityViolationException? = null

    repeat(MAX_RETRY_COUNT) { attempt ->
        try {
            return transactionTemplate.execute {
                val accountNumber = accountNumberGenerator.generate()
                val account = Account(
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
```

**주요 설계 결정:**
- `TransactionTemplate`: self-invocation 문제를 피하기 위해 프로그래매틱 트랜잭션 사용
- `AccountNumberGenerator`: 테스트 용이성을 위해 계좌번호 생성 로직 분리
- `initCause()`: 디버깅을 위해 원본 예외 정보 보존

**상태:** 해결됨

---

### 2. Double Submit (중복 요청)

**문제점:**
- 클라이언트가 동일 요청을 여러 번 전송하면 같은 사람 명의로 여러 계좌가 생성됨

**비즈니스 요구사항:**
- 같은 사람이 여러 계좌를 보유할 수 있음
- 따라서 Double Submit으로 인한 중복 계좌 생성은 비즈니스적으로 허용됨

**결론:**
- 별도 처리 불필요
- 향후 요구사항 변경 시 멱등성 키(Idempotency Key) 도입 고려

**상태:** 해당 없음 (비즈니스적으로 허용)

---

## 우선순위 요약

| 문제 | 우선순위 | 해결책 | 상태 |
|------|---------|--------|------|
| 계좌번호 중복 | 높음 | DB unique 제약조건 + TransactionTemplate 재시도 | 해결됨 |
| Double Submit | - | 비즈니스적으로 허용 (다중 계좌 가능) | 해당 없음 |
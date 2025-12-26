# 계좌(Account) 도메인 동시성 이슈

## 계좌 생성 (createAccount)

### 1. 계좌번호 중복 가능성

**문제점:**
- UUID를 12자로 자르면 충돌 확률이 급격히 증가
- 동시 요청 시 중복 계좌번호 생성 가능

**해결 방안:**
- DB에 `accountNumber` unique 제약조건 추가
- 저장 전 중복 체크 + 최대 3회 재시도 로직 구현

**적용된 구현:**
```kotlin
// Account.kt - unique 제약조건
@Column(unique = true, nullable = false)
val accountNumber: String

// AccountService.kt - 재시도 로직
repeat(MAX_RETRY_COUNT) {
    val accountNumber = generateAccountNumber()
    if (!accountRepository.existsByAccountNumber(accountNumber)) {
        return accountRepository.save(Account(...))
    }
}
throw IllegalStateException("계좌번호 생성에 실패했습니다.")
```

**상태:** 해결됨

---

### 2. Double Submit (중복 요청)

**문제점:**
- 클라이언트가 동일 요청을 여러 번 전송하면 같은 사람 명의로 여러 계좌가 생성됨

**해결 방안:**
- 멱등성 키(Idempotency Key) 도입
- Request에 고유 `requestId`를 포함시켜 중복 체크

**상태:** 미해결

---

### 3. DB Unique Constraint 충돌 처리

**문제점:**
- 계좌번호 중복 시 예외만 발생하고 복구 로직이 없음

**해결 방안:**
- 저장 전 `existsByAccountNumber()`로 중복 체크
- 최대 3회 재시도 후 실패 시 예외 발생

**상태:** 해결됨 (이슈 #1과 함께 해결)

---

## 우선순위 요약

| 문제 | 우선순위 | 해결책 | 상태 |
|------|---------|--------|------|
| 계좌번호 중복 | 높음 | DB unique 제약조건 + 재시도 | 해결됨 |
| Double Submit | 중간 | 멱등성 키 도입 | 미해결 |
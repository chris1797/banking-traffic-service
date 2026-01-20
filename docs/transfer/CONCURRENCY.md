# 이체(Transfer) 도메인 동시성 이슈

## 이체 (transfer)

### 1. Deadlock 위험

**문제점:**
- 두 계좌를 동시에 수정할 때 락 순서가 일관되지 않으면 Deadlock 발생 가능

**시나리오:**
```
스레드 1: A -> B 이체 (A 락 획득 -> B 락 대기)
스레드 2: B -> A 이체 (B 락 획득 -> A 락 대기)
-> Deadlock!
```

**해결 방안:**
- 계좌번호를 정렬하여 항상 동일한 순서로 조회/수정
- 낙관적 락으로 충돌 감지 및 재시도

**구현:**
```kotlin
// Deadlock 방지: 계좌번호를 정렬하여 일관된 순서로 조회
val sortedAccountNumbers = listOf(request.fromAccountNumber, request.toAccountNumber).sorted()
val firstAccountNumber = sortedAccountNumbers[0]
val secondAccountNumber = sortedAccountNumbers[1]

val firstAccount = accountRepository.findByAccountNumber(firstAccountNumber)
val secondAccount = accountRepository.findByAccountNumber(secondAccountNumber)
```

**상태:** 해결됨

---

### 2. Lost Update (두 계좌 동시 수정)

**문제점:**
- 출금/입금이 각각 다른 트랜잭션에서 수행되면 일관성 깨짐

**해결 방안:**
- 단일 트랜잭션 내에서 두 계좌 모두 수정
- 낙관적 락(@Version)으로 충돌 감지

**구현:**
```kotlin
transactionTemplate.execute {
    // 하나의 트랜잭션 내에서 양 계좌 수정
    fromAccount.withdraw(request.amount)
    toAccount.deposit(request.amount)
    transferEntity.success()
}
```

**상태:** 해결됨

---

### 3. Transfer 상태와 Account 잔액 불일치

**문제점:**
- Transfer는 SUCCESS인데 Account 잔액 변경이 롤백될 수 있음

**해결 방안:**
- 같은 트랜잭션 내에서 Transfer 상태와 Account 잔액 동시 변경
- 실패 시 별도 트랜잭션으로 Transfer 상태를 FAILED로 변경

**구현:**
```kotlin
// 성공 시: 같은 트랜잭션 내에서 처리
transactionTemplate.execute {
    fromAccount.withdraw(request.amount)
    toAccount.deposit(request.amount)
    transferEntity.success()  // 같은 트랜잭션
}

// 실패 시: 별도 트랜잭션으로 FAILED 상태 업데이트
private fun handleTransferFailure(transferId: Long, reason: String) {
    transactionTemplate.execute {
        val entity = transferRepository.findById(transferId).orElse(null)
        entity?.fail(reason)
    }
}
```

**상태:** 해결됨

---

### 4. 낙관적 락 충돌 시 무한 재시도

**문제점:**
- 트래픽이 많을 때 낙관적 락 충돌이 반복되면 무한 재시도 가능

**해결 방안:**
- 최대 재시도 횟수 제한 (MAX_RETRY_COUNT = 10)
- 초과 시 TRANSFER_FAILED 예외 발생

**상태:** 해결됨

---

## 우선순위 테이블

| 문제 | 우선순위 | 해결책 | 상태 |
|------|---------|--------|------|
| Deadlock 위험 | 높음 | 계좌번호 정렬로 일관된 락 순서 | 해결됨 |
| Lost Update | 높음 | 단일 트랜잭션 + 낙관적 락 | 해결됨 |
| Transfer-Account 불일치 | 중간 | 같은 트랜잭션 내 상태 변경 | 해결됨 |
| 무한 재시도 | 중간 | 최대 재시도 횟수 제한 | 해결됨 |

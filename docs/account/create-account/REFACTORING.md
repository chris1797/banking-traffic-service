# 계좌 생성 기능 리팩토링 기록

## 개요

계좌 생성(`createAccount`) 기능에서 발견된 동시성 문제 및 코드 품질 이슈를 분석하고 해결한 과정을 기록.

---

## 1. ⚠️ Race Condition (Check-then-Act 패턴)

### 기존 코드
```kotlin
if (!accountRepository.existsByAccountNumber(accountNumber)) {
    return accountRepository.save(account)
}
```

### 문제점
- `existsByAccountNumber()` 체크와 `save()` 사이에 다른 트랜잭션이 같은 계좌번호로 저장 가능
- 전형적인 "Check-then-Act" Race Condition 패턴

```
시간 →
스레드 A: existsByAccountNumber("1234") → false
스레드 B: existsByAccountNumber("1234") → false  ← 같은 번호!
스레드 A: save() → 성공
스레드 B: save() → DataIntegrityViolationException 💥
```

### 해결
- `existsByAccountNumber()` 체크 제거
- DB unique 제약조건을 신뢰하고, `DataIntegrityViolationException` catch 후 재시도하는 로직으로 변경

---

## 2. 재시도 로직 버그

### 기존 코드
```kotlin
repeat(MAX_RETRY_COUNT) {
    val accountNumber = generateAccountNumber()
    if (!accountRepository.existsByAccountNumber(accountNumber)) {
        val account = Account(...)
        try {
            return accountRepository.save(account)
        } catch (e: DataIntegrityViolationException) {
            // 재시도해야 하는데...
        }
    }
    throw CoreException(ErrorType.CREATE_ACCOUNT_FAILED)  // ❌ repeat 안에 있음!
}
```

### 문제점
- `throw`가 `repeat` 블록 **안에** 있어서 첫 시도에서 바로 예외 발생
- catch 블록 진입 후 다음 줄에서 바로 예외가 던져져 재시도 불가

### 해결
- `throw`를 `repeat` 블록 **밖으로** 이동
- 모든 재시도가 실패한 경우에만 예외 발생

---

## 3. ⚠️️ @Transactional Self-Invocation 문제

### 생각했던 코드
```kotlin
fun createAccount(...) {
    repeat(MAX_RETRY_COUNT) {
        try {
            return saveAccount(account)  // 내부 호출
        } catch (e: DataIntegrityViolationException) {
            // 재시도
        }
    }
}

@Transactional
fun saveAccount(...) {  // 트랜잭션 적용 안 됨!
    return accountRepository.save(account)
}
```

### 문제점
- Spring `@Transactional`은 프록시 기반으로 동작
- 같은 클래스 내 메서드 호출(self-invocation)은 프록시를 거치지 않음
- 트랜잭션이 적용되지 않아 재시도 시에도 롤백 마킹된 트랜잭션 문제 발생

```
외부 호출: Client → Proxy(@Transactional 처리) → 실제 객체
내부 호출: 실제 객체 → 실제 객체 (프록시를 거치지 않음!)
```

### 해결
- `TransactionTemplate` 사용하여 조금 더 명시적으로 트랜잭션 경계 설정
- 각 재시도마다 독립적인 트랜잭션 보장
- 별도 클래스 분리 없이 같은 클래스에서 해결 가능

---

## 4. 테스트 용이성 부족

### 기존 코드

- private 메서드로 계좌번호 생성 로직을 캡슐화 했었음.
```kotlin
private fun generateAccountNumber(): String {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 12)
}
```

### 문제점
- private 메서드 + 비결정적(랜덤) 반환값
- 테스트에서 특정 계좌번호 주입 불가로, 중복 충돌 시나리오 테스트 어려움

### 해결
- `AccountNumberGenerator` 인터페이스 분리 및 `UuidAccountNumberGenerator` 구현체 생성 
- 기존 계좌번호 생성 로직은 그대로 유지
- 생성자 주입으로 테스트에서 mock 가능

```kotlin
interface AccountNumberGenerator {
    fun generate(): String
}

@Component
class UuidAccountNumberGenerator : AccountNumberGenerator {
    override fun generate(): String {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12)
    }
}
```

---

## 5. 로깅 및 원인 추적 부재 +

### 기존 코드
```kotlin
catch (e: DataIntegrityViolationException) {
    // 아무것도 안 함
}
// ...
throw CoreException(ErrorType.CREATE_ACCOUNT_FAILED)  // 원인 정보 손실
```

### 문제점
- 재시도 발생 여부를 알 수 없음 (로그 없음)
- 예외 발생 시 원본 예외 정보 손실 (디버깅 어려움)

### 해결
- warn 레벨 로그 추가 (재시도 횟수 포함)
- `initCause(lastException)`로 원본 예외 보존

```kotlin
catch (e: DataIntegrityViolationException) {
    lastException = e
    log.warn("계좌번호 충돌 발생, 재시도 중 (시도: ${attempt + 1}/$MAX_RETRY_COUNT)")
}
// ...
throw CoreException(ErrorType.CREATE_ACCOUNT_FAILED).initCause(lastException)
```

---

## 최종 코드

```kotlin
@Service
class AccountService(
    private val accountRepository: AccountRepository,
    private val accountNumberGenerator: AccountNumberGenerator,
    private val transactionTemplate: TransactionTemplate
) {
    private val log = LoggerFactory.getLogger(AccountService::class.java)

    companion object {
        private const val MAX_RETRY_COUNT = 3
    }

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
}
```

---

## 변경된 파일 목록

| 파일 | 변경 내용 |
|------|----------|
| `AccountService.kt` | TransactionTemplate 적용, 로깅 추가 |
| `AccountNumberGenerator.kt` | 인터페이스 신규 생성 |
| `UuidAccountNumberGenerator.kt` | 구현체 신규 생성 |
| `AccountServiceTest.kt` | 단위 테스트 추가 |
| `build.gradle.kts` | MockK 의존성 추가 |

---

## 교훈

1. **Check-then-Act 패턴 지양**: DB 제약조건을 신뢰하고 예외 처리로 대응
2. **@Transactional 프록시 이해**: self-invocation 시 트랜잭션 미적용 주의
3. **테스트 가능한 설계**: 외부 의존성(랜덤, 시간 등)은 인터페이스로 분리
4. **로깅과 원인 추적**: 운영 환경 디버깅을 위한 충분한 정보 기록
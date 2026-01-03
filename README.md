# Banking Traffic Service

뱅킹 API 서버의 동시성 이슈를 정의하고 트래픽 부하 테스트를 진행하는 프로젝트

## 기술 스택

- Kotlin + Spring Boot 3.5
- Spring Data JPA + PostgreSQL
- Gradle (Kotlin DSL)

## 구현 기능 (동시성 이슈 중심)

### 1. 계좌 (Account)
| 기능 | 설명 | 동시성 이슈 |
|------|------|-------------|
| 계좌 생성 | 신규 계좌 개설 | - |
| 계좌 조회 | 잔액 및 계좌 정보 조회 | Dirty Read |
| 입금 | 계좌에 금액 입금 | Lost Update |
| 출금 | 계좌에서 금액 출금 | Lost Update, Race Condition |

### 2. 이체 (Transfer)
| 기능 | 설명 | 동시성 이슈 |
|------|------|-------------|
| 계좌 이체 | A계좌 → B계좌 송금 | Deadlock, Lost Update |
| 동시 이체 | 같은 계좌에 동시 이체 요청 | Race Condition |

### 3. 결제 (Payment)
| 기능 | 설명 | 동시성 이슈 |
|------|------|-------------|
| 결제 요청 | 상품/서비스 결제 | Double Spending |
| 결제 취소 | 결제 취소 및 환불 | Lost Update |

### 4. 포인트 (Point)
| 기능 | 설명 | 동시성 이슈 |
|------|------|-------------|
| 포인트 적립 | 거래 시 포인트 적립 | Lost Update |
| 포인트 사용 | 포인트로 결제 | Race Condition, Overselling |

### 5. 한도 (Limit)
| 기능 | 설명 | 동시성 이슈 |
|------|------|-------------|
| 일일 이체 한도 | 하루 이체 금액 제한 | Race Condition |
| 1회 이체 한도 | 건당 이체 금액 제한 | - |

## 동시성 이슈 유형

[//]: # ()
[//]: # (| 이슈 | 설명 |)

[//]: # (|------|------|)

[//]: # (| **Lost Update** | 동시 수정 시 한쪽 업데이트가 유실 |)

[//]: # (| **Race Condition** | 여러 요청이 동시에 같은 자원 접근 |)

[//]: # (| **Deadlock** | 두 트랜잭션이 서로 락을 대기 |)

[//]: # (| **Dirty Read** | 커밋되지 않은 데이터 읽기 |)

[//]: # (| **Double Spending** | 동일 자원을 중복 사용 |)

[//]: # (| **Overselling** | 재고/잔액 초과 판매 |)

## 동시성 해결 전략

[//]: # ()
[//]: # (| 전략 | 적용 케이스 |)

[//]: # (|------|-------------|)

[//]: # (| **Pessimistic Lock** | 충돌 빈도 높은 경우 &#40;출금, 이체&#41; |)

[//]: # (| **Optimistic Lock** | 충돌 빈도 낮은 경우 &#40;조회 위주&#41; |)

[//]: # (| **Distributed Lock &#40;Redis&#41;** | 다중 인스턴스 환경 |)

[//]: # (| **Idempotency Key** | 중복 요청 방지 &#40;결제&#41; |)

## 상세 문서

| 도메인 | 문서 | 설명 |
|--------|------|------|
| Account | [계좌 생성 동시성 이슈](docs/account/create-account/CONCURRENCY.md) | 계좌 생성 관련 동시성 문제 |
| Account | [계좌 생성 리팩토링](docs/account/create-account/REFACTORING.md) | 계좌 생성 기능 문제점 및 해결 과정 |
| Account | [입금 동시성 이슈](docs/account/deposit/CONCURRENCY.md) | 입금 관련 동시성 문제 (Lost Update, Double Submit) |

## 부하 테스트

- 도구: K6? Gatling? JMeter? nGrinder?
- 시나리오별 동시 요청 테스트
- TPS, 응답시간, 에러율 측정

## 프로젝트 구조

```
src/main/kotlin/com/banking/core/
├── api/    # REST API
├── service/       # 비즈니스 로직
├── repository/    # 데이터 접근
├── domain/        # 엔티티
├── dto/           # 요청/응답 DTO
├── config/        # 설정
├── exception/     # 예외 처리
└── common/        # 공통 유틸
```

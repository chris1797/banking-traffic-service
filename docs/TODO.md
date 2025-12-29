# TODO

## 1. 프로젝트 기본 설정

- [x] PostgreSQL 연결 설정 (application.yml) - 로컬 DB 사용
- [x] Docker Compose 설정 (Redis, K6, InfluxDB, Grafana)
- [x] 공통 응답 포맷 정의 (ApiResponse)
- [x] 전역 예외 핸들러 구현 (ControllerAdvice)
- [x] Base Entity 생성 (id, createdAt, updatedAt)
- [x] 커스텀 예외 정의 (CoreException, ErrorType)
- [x] Makefile (명령어 간소화)

## 2. 계좌 (Account)

- [x] Account 엔티티
- [x] AccountRepository
- [ ] AccountService (생성, 조회, 입금, 출금)
  - [x] 계좌 생성 (createAccount)
  - [ ] 계좌 조회
  - [ ] 입금
  - [ ] 출금
- [x] AccountController
- [x] AccountNumberGenerator (인터페이스 분리)
- [ ] 단위 테스트
  - [x] AccountServiceTest (계좌 생성)

## 3. 이체 (Transfer)

- [ ] Transfer 엔티티 (이체 내역)
- [ ] TransferRepository
- [ ] TransferService (이체 로직)
- [ ] TransferController
- [ ] 단위 테스트

## 4. 결제 (Payment)

- [ ] Payment 엔티티
- [ ] PaymentRepository
- [ ] PaymentService (결제, 취소)
- [ ] PaymentController
- [ ] 단위 테스트

## 5. 포인트 (Point)

- [ ] Point 엔티티
- [ ] PointHistory 엔티티
- [ ] PointRepository
- [ ] PointService (적립, 사용)
- [ ] PointController
- [ ] 단위 테스트

## 6. 한도 (Limit)

- [ ] DailyLimit 엔티티
- [ ] LimitRepository
- [ ] LimitService (한도 체크, 갱신)
- [ ] 단위 테스트

## 7. 동시성 제어

- [ ] Pessimistic Lock 적용 (출금, 이체)
- [ ] Optimistic Lock 적용 (@Version)
- [ ] Redis 분산 락 구현
- [ ] Idempotency Key 처리

## 8. 부하 테스트

- [x] 테스트 도구 선정 (K6)
- [x] 인프라 구성 (InfluxDB, Grafana)
- [x] ARM64(Apple Silicon) 호환성 설정
- [x] K6 사용 가이드 문서 (k6/README.md)
- [x] Grafana 데이터소스 프로비저닝 설정
- [ ] 시나리오 작성
  - [x] 계좌 생성 (account-create.js)
  - [ ] 입금/출금
  - [ ] 이체
- [ ] 테스트 실행 및 결과 분석

# K6 부하 테스트 가이드

## 사전 준비

1. Docker & Docker Compose 설치
2. 애플리케이션 실행 (localhost:8080)

## 실행 방법

### 1. 기본 실행 (Redis만)

```bash
# Redis 실행
docker compose up -d redis

# 애플리케이션 실행
./gradlew bootRun
```

### 2. 부하 테스트 실행

#### 방법 A: Docker로 실행 (권장)
```bash
# 부하 테스트 인프라 실행 (InfluxDB, Grafana)
docker compose --profile loadtest up -d influxdb grafana

# K6 테스트 실행
docker compose --profile loadtest run --rm k6 run /scripts/scenarios/account-create.js
```

#### 방법 B: 로컬 K6로 실행
```bash
# K6 설치 (macOS)
brew install k6

# 테스트 실행
k6 run k6/scenarios/account-create.js
```

### 3. 결과 확인

- **콘솔**: 테스트 완료 후 요약 출력
- **Grafana**: http://localhost:3000 (InfluxDB 연동 시)
- **JSON 결과**: `k6/results/account-create-summary.json`

## 테스트 시나리오

### account-create.js

계좌 생성 API 부하 테스트

| 단계 | 시간 | VU (가상 사용자) |
|------|------|-----------------|
| Ramp Up | 30s | 0 → 10 |
| 유지 | 1m | 10 |
| Ramp Up | 30s | 10 → 50 |
| 유지 | 1m | 50 |
| Ramp Down | 30s | 50 → 0 |

**임계값 (Thresholds):**
- 95% 응답시간 < 500ms
- 실패율 < 1%

## 환경 변수

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `BASE_URL` | `http://host.docker.internal:8080` | API 서버 주소 |

```bash
# 커스텀 URL로 실행
k6 run -e BASE_URL=http://localhost:8080 k6/scenarios/account-create.js
```

## 새 시나리오 추가

`k6/scenarios/` 디렉토리에 새 파일 생성:

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 10,
    duration: '30s',
};

export default function () {
    // 테스트 로직
}
```

## 서비스 중지

```bash
# 전체 중지
docker compose --profile loadtest down

# 볼륨까지 삭제
docker compose --profile loadtest down -v
```
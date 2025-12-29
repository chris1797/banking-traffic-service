.PHONY: up down redis test-account test-local

# Redis 실행
redis:
	docker compose up -d redis

# 부하 테스트 인프라 실행
up:
	docker compose --profile loadtest up -d

# 전체 종료
down:
	docker compose --profile loadtest down

# 계좌 생성 부하 테스트 (Docker)
test-account:
	docker compose --profile loadtest run --rm k6 run /scripts/scenarios/account-create.js

# 계좌 생성 부하 테스트 (로컬 K6)
test-local:
	k6 run k6/scenarios/account-create.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

// 테스트 설정
export const options = {
    scenarios: {
        // 시나리오 1: 점진적 부하 증가
        ramp_up: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 10 },  // 30초 동안 10 VU까지 증가
                { duration: '1m', target: 10 },   // 1분간 10 VU 유지
                { duration: '30s', target: 50 },  // 30초 동안 50 VU까지 증가
                { duration: '1m', target: 50 },   // 1분간 50 VU 유지
                { duration: '30s', target: 0 },   // 30초 동안 0으로 감소
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],  // 95% 요청이 500ms 이내
        http_req_failed: ['rate<0.01'],     // 실패율 1% 미만
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';

// 계좌 생성 테스트
export default function () {
    const payload = JSON.stringify({
        holderName: `테스트유저_${randomString(8)}`,
        initialBalance: Math.floor(Math.random() * 1000000),
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const response = http.post(`${BASE_URL}/v1/account`, payload, params);

    check(response, {
        'status is 200': (r) => r.status === 200,
        'response has accountNumber': (r) => {
            const body = JSON.parse(r.body);
            return body.data && body.data.accountNumber;
        },
    });

    sleep(0.1);  // 100ms 대기
}

// 테스트 종료 후 요약
export function handleSummary(data) {
    return {
        'stdout': textSummary(data, { indent: ' ', enableColors: true }),
        './k6/results/account-create-summary.json': JSON.stringify(data),
    };
}

function textSummary(data, options) {
    const { metrics } = data;
    return `
========== 계좌 생성 부하 테스트 결과 ==========

총 요청 수: ${metrics.http_reqs.values.count}
성공률: ${((1 - metrics.http_req_failed.values.rate) * 100).toFixed(2)}%
평균 응답시간: ${metrics.http_req_duration.values.avg.toFixed(2)}ms
95% 응답시간: ${metrics.http_req_duration.values['p(95)'].toFixed(2)}ms
최대 응답시간: ${metrics.http_req_duration.values.max.toFixed(2)}ms

================================================
`;
}
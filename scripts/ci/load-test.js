import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '20s', target: 10 },
    { duration: '40s', target: 20 },
    { duration: '40s', target: 20 },
    { duration: '20s', target: 0 },
  ],
  thresholds: {
    'http_req_duration': ['p(95)<500'],
    'http_req_failed': ['rate<0.1'],
  },
};

const BASE_URL = 'http://localhost:8080';

const endpoints = [
  { path: '/api/content', name: 'Content' },
  { path: '/api/trending', name: 'Trending' },
  { path: '/q/health/ready', name: 'Health' },
];

export default function () {
  for (const ep of endpoints) {
    const res = http.get(`${BASE_URL}${ep.path}`);
    const ok = check(res, {
      [`${ep.name} OK`]: (r) => r.status === 200,
      [`${ep.name} fast`]: (r) => r.timings.duration < 1000,
    });
    errorRate.add(!ok);
  }
  sleep(1);
}

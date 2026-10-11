// Нагрузочный сценарий «полный user journey» (k6).
//
// Что гоняет каждая VU-итерация (steady state):
//   1. сессия: GET /users/profile, при 401 — повторный POST /auth/login
//   2. портал: ленивое создание POST /portals на первой итерации, далее GET /portals
//   3. заявка: POST /portals/{id}/tickets → ответ POST .../messages → GET .../messages
//   4. поиск: GET /tickets?mine=true&search=Load  (горячий путь из n1.md)
//   5. уведомления: GET /notifications/unread-count + long polling GET /notifications/wait
//   6. refresh access: POST /auth/tokens/access каждую 10-ю итерацию
//
// Пользователи НЕ регистрируются в сценарии: пул load-pool-{RUN_ID}-pN@load.test
// сидирует scripts/load/run.sh (регистрация 10/300с на IP + ручная верификация
// email через psql — порталы создают только верифицированные пользователи).
//
// Рейт-лимиты: каждый запрос несёт уникальный X-Forwarded-For по номеру VU,
// поэтому у каждой VU свой bucket лимита. Это работает только когда стек
// поднят с load-оверлеем (TRUST_PROXY_HEADERS=true): make up-load / make load.
// Без него все VU делят 127.0.0.1 и логин упрётся в 10/300с.
//
// CSRF: изменяющие методы несут X-Requested-With (CsrfProtectionFilter).
// TLS: локальный keystore самоподписанный — insecureSkipTLSVerify в options.
//
// Запуск: make load (полный) или make load-smoke (2 VU, 1 минута).
// Параметры через окружение: TARGET_VUS, DURATION, POOL_SIZE, THINK_TIME,
// WAIT_TIMEOUT_SEC, RUN_ID (см. Makefile и run.sh).

import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'https://localhost:8443';
const API = `${BASE}/api/v1`;
const RUN_ID = __ENV.RUN_ID || 'local';
const POOL_SIZE = Number(__ENV.POOL_SIZE || 8);
const TARGET_VUS = Number(__ENV.TARGET_VUS || 10);
const DURATION = __ENV.DURATION || '5m';
const THINK_TIME = Number(__ENV.THINK_TIME || 1);
const WAIT_TIMEOUT_SEC = Number(__ENV.WAIT_TIMEOUT_SEC || 3);
const PASSWORD = 'Password123!';

export const options = {
  insecureSkipTLSVerify: true,
  scenarios: {
    user_journey: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: TARGET_VUS },
        { duration: DURATION, target: TARGET_VUS },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    //429 от рейт-лимитов сюда же: если TRUST_PROXY_HEADERS не включён,
    // порог упадёт с первыми же параллельными логинами — это сигнал, а не шум
    http_req_failed: ['rate<0.01'],
    // глобальный порог мягкий: notifications_wait — намеренный long-poll (timeoutSec=WAIT_TIMEOUT_SEC,
    // по умолчанию 3s) и доминирует в p(95); точные пороги — ниже, по endpoint'ам
    http_req_duration: ['p(95)<5000'],
    'http_req_duration{endpoint:search}': ['p(95)<500'],
    'http_req_duration{endpoint:ticket_create}': ['p(95)<1200'],
    'http_req_duration{endpoint:notifications_wait}': ['p(95)<6000'],
    checks: ['rate>0.99'],
  },
};

// per-VU состояние: живёт между итерациями в рамках одной VU.
// Cookie jar — явная и ленивая: встроенная k6-jar сбрасывается между
// итерациями (без неё каждый виток теряет accessToken и логинится заново),
// а http.cookieJar() нельзя вызывать в init-контексте — только внутри VU.
let vuEmail = null;
let portalId = null;
let jar = null;

function getJar() {
  if (jar === null) {
    jar = http.cookieJar();
  }
  return jar;
}

function headers() {
  const octet = 1 + (__VU % 250);
  const net = 10 + Math.floor(__VU / 250);
  return {
    'Content-Type': 'application/json',
    'X-Requested-With': 'XMLHttpRequest',
    // уникальный клиентский IP на VU — изоляция рейт-лимитов (см. шапку)
    'X-Forwarded-For': `10.77.${net}.${octet}`,
  };
}

function tag(endpoint) {
  return { headers: headers(), tags: { endpoint }, jar: getJar() };
}

function emailForVU() {
  // VU1 → p1, VU2 → p2, ... VU9 → p1 — пользователей в пуле меньше, чем VU
  const n = ((__VU - 1) % POOL_SIZE) + 1;
  return `load-pool-${RUN_ID}-p${n}@load.test`;
}

function ensureSession() {
  let res = http.get(`${API}/users/profile`, tag('profile'));
  if (res.status === 200) {
    check(res, { 'profile: 200': () => true });
    return;
  }
  check(res, { 'profile: 200 или re-login': (r) => r.status === 401 });
  res = http.post(
    `${API}/auth/login`,
    JSON.stringify({ email: vuEmail, password: PASSWORD }),
    tag('login'),
  );
  check(res, { 'login: 200': (r) => r.status === 200 });
}

function ensurePortal() {
  if (portalId !== null) {
    const res = http.get(`${API}/portals`, tag('portals_list'));
    check(res, { 'portals: 200': (r) => r.status === 200 });
    return;
  }
  const res = http.post(
    `${API}/portals`,
    JSON.stringify({ name: `load-portal-${__VU}`, description: 'нагрузочный портал' }),
    tag('portal_create'),
  );
  check(res, { 'portal: 201': (r) => r.status === 201 });
  if (res.status === 201) {
    portalId = res.json('id');
  }
}

function ticketJourney() {
  const created = http.post(
    `${API}/portals/${portalId}/tickets`,
    JSON.stringify({
      title: `Load ticket v${__VU} i${__ITER}`,
      body: 'Нагрузочная заявка пользовательского сценария',
      ticketPriority: 'MEDIUM',
      ticketAccessStatus: 'ALL_USERS',
    }),
    tag('ticket_create'),
  );
  check(created, { 'ticket: 201': (r) => r.status === 201 });
  if (created.status !== 201) return;
  const ticketId = created.json('id');

  const message = http.post(
    `${API}/portals/${portalId}/tickets/${ticketId}/messages`,
    JSON.stringify({ messageText: `Ответ на заявку, итерация ${__ITER}` }),
    tag('message_create'),
  );
  check(message, { 'message: 201': (r) => r.status === 201 });

  const list = http.get(
    `${API}/portals/${portalId}/tickets/${ticketId}/messages`,
    tag('messages_list'),
  );
  check(list, { 'messages: 200': (r) => r.status === 200 });
}

function searchTickets() {
  const res = http.get(`${API}/tickets?mine=true&search=Load&size=10`, tag('search'));
  check(res, { 'search: 200': (r) => r.status === 200 });
}

function notifications() {
  const unread = http.get(`${API}/notifications/unread-count`, tag('unread_count'));
  check(unread, { 'unread-count: 200': (r) => r.status === 200 });

  // long polling: часто пустой ответ по таймауту — это нормальный путь
  const wait = http.get(
    `${API}/notifications/wait?timeoutSec=${WAIT_TIMEOUT_SEC}&afterId=0&size=10`,
    tag('notifications_wait'),
  );
  check(wait, { 'wait: 200': (r) => r.status === 200 });
}

function maybeRefreshAccess() {
  if (__ITER > 0 && __ITER % 10 === 0) {
    const res = http.post(`${API}/auth/tokens/access`, null, tag('token_refresh'));
    check(res, { 'refresh: 200': (r) => r.status === 200 });
  }
}

export default function () {
  if (vuEmail === null) {
    vuEmail = emailForVU();
  }

  group('journey', () => {
    ensureSession();
    ensurePortal();
    ticketJourney();
    searchTickets();
    notifications();
    maybeRefreshAccess();
  });

  sleep(THINK_TIME);
}

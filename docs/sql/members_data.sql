-- 데모 계정 초기 데이터
-- 공통 데모 비밀번호: Devmozip123! (BCrypt 해시로 저장)
-- 같은 이메일이 이미 있으면 넣지 않으므로 서버를 여러 번 시작해도 중복되지 않는다.
-- career는 경력을 개월 수로 저장한다. (예: 4년 차 → 48)

-- 김하늘 · 일반 회원
INSERT INTO users (email, password_hash, nickname, tag, role, status, trust_score, collaboration_status, summary, career)
SELECT 'member@devmozip.demo', '$2a$10$QPCAzVrhreUcnT./3zMDg.f.Wn5nj8cbsl8DD4M3BkKVXEfwMBY1u',
       '김하늘', 'member', 'USER', 'ACTIVE', 36.50, 'AVAILABLE', '프론트엔드 개발자', 36
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'member@devmozip.demo');

-- 이서준 · 프리미엄 / Pocket 주최자
INSERT INTO users (email, password_hash, nickname, tag, role, status, trust_score, collaboration_status, summary, career)
SELECT 'premium@devmozip.demo', '$2a$10$QPCAzVrhreUcnT./3zMDg.f.Wn5nj8cbsl8DD4M3BkKVXEfwMBY1u',
       '이서준', 'premium', 'PREMIUM', 'ACTIVE', 36.50, 'AVAILABLE', '백엔드 개발자', 48
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'premium@devmozip.demo');

-- 데브모집 관리자 · 관리자
INSERT INTO users (email, password_hash, nickname, tag, role, status, trust_score, collaboration_status, summary, career)
SELECT 'admin@devmozip.demo', '$2a$10$QPCAzVrhreUcnT./3zMDg.f.Wn5nj8cbsl8DD4M3BkKVXEfwMBY1u',
       '데브모집 관리자', 'admin', 'ADMIN', 'ACTIVE', 36.50, 'RESTING', '풀스택 개발자', 60
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@devmozip.demo');

-- [테스트용] 정지된 회원 · 로그인 시 403 응답 확인용
INSERT INTO users (email, password_hash, nickname, tag, role, status, trust_score, collaboration_status, summary, career)
SELECT 'suspended@devmozip.demo', '$2a$10$QPCAzVrhreUcnT./3zMDg.f.Wn5nj8cbsl8DD4M3BkKVXEfwMBY1u',
       '정지된 회원', 'suspended', 'USER', 'SUSPENDED', 36.50, 'RESTING', '정지 계정 테스트', NULL
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'suspended@devmozip.demo');

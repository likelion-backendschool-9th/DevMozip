import 'server-only';

export const BACKEND_URL = process.env.BACKEND_URL ?? 'http://localhost:8080';
// 운영 빌드는 AUTH_MODE=mock을 주더라도 실제 인증만 사용한다.
export const authMode = () => process.env.NODE_ENV === 'production' || process.env.AUTH_MODE === 'server' ? 'server' : 'mock';

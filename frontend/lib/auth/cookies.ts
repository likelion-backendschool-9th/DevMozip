export const ACCESS_COOKIE = 'devmozip_access';
export const REFRESH_COOKIE = 'devmozip_refresh';
export const ACCESS_MAX_AGE = 30 * 60;
export const REFRESH_MAX_AGE = 14 * 24 * 60 * 60;
// 카카오 인증을 시작할 때 저장해 콜백에서 대조한다.
export const KAKAO_FLOW_COOKIE = 'devmozip_kakao_flow';
export const KAKAO_FLOW_MAX_AGE = 10 * 60;
// 가입 폼을 제출할 때까지 카카오 가입 토큰을 보관한다. 값은 백엔드 토큰 수명과 맞춘다.
export const KAKAO_SIGNUP_COOKIE = 'devmozip_kakao_signup';
export const KAKAO_SIGNUP_MAX_AGE = 30 * 60;
export const WITHDRAWAL_KAKAO_COOKIE = 'devmozip_withdrawal_kakao';
export const cookieOptions = {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax' as const,
    path: '/',
};

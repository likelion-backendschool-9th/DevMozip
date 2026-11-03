export const ACCESS_COOKIE = 'devmozip_access';
export const REFRESH_COOKIE = 'devmozip_refresh';
export const ACCESS_MAX_AGE = 30 * 60;
export const REFRESH_MAX_AGE = 14 * 24 * 60 * 60;
export const cookieOptions = {
    httpOnly: true,
    secure: process.env.NODE_ENV === 'production',
    sameSite: 'lax' as const,
    path: '/',
};

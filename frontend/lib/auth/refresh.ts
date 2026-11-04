import 'server-only';
import { BACKEND_URL } from './env';
export async function refreshAccessToken(refreshToken: string): Promise<string | null> {
    try {
        const response = await fetch(`${BACKEND_URL}/api/v1/auth/refresh`, {
            method: 'POST', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken }), cache: 'no-store', signal: AbortSignal.timeout(5000),
        });
        if (!response.ok) return null;
        const data = await response.json();
        return typeof data.accessToken === 'string' ? data.accessToken : null;
    } catch { return null; }
}

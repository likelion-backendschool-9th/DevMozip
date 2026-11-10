export type AccountRole = 'free' | 'premium' | 'admin';
export interface AuthUser { onboardingCompleted?:boolean; id: string; email: string; nickname: string; role: AccountRole; job: string; career: string; stacks: string[]; interests: string[]; available: boolean }
export interface SignupInput { email: string; password: string; confirmPassword: string; nickname: string; job: string; career: string; skills: string[]; interests: string[]; available: boolean }
export interface SignupOptions { jobCategories: string[]; skills: string[]; interests: string[] }
export type SignupErrors = Partial<Record<keyof SignupInput | 'checks', string>>;
export type KakaoIntent = 'login' | 'signup' | 'link' | 'unlink';
export type KakaoResult = 'signup' | 'already-registered' | 'linked' | 'unlinked' | 'cancelled' | 'invalid-state' | 'failed' | 'suspended' | 'unavailable' | 'conflict' | 'unlink-blocked';
export interface KakaoAccountStatus { kakaoLinked: boolean; passwordSet: boolean }
export interface PasswordSetInput { newPassword: string; confirmPassword: string }
export interface PasswordResetInput extends PasswordSetInput { token: string }

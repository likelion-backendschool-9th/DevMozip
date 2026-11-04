export type AccountRole = 'free' | 'premium' | 'admin';
export interface AuthUser { onboardingCompleted?:boolean; id: string; email: string; nickname: string; role: AccountRole; job: string; career: string; stacks: string[]; interests: string[]; available: boolean }
export interface SignupInput { email: string; password: string; confirmPassword: string; nickname: string; job: string; career: string; stacks: string[]; interests: string[]; available: boolean }
export type SignupErrors = Partial<Record<keyof SignupInput | 'checks', string>>;

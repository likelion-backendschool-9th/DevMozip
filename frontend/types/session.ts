export type AuthMode = 'mock' | 'server';
export interface ServerIdentity { username: string; role: 'USER' | 'PREMIUM' | 'OWNER' | 'ADMIN' }
export interface SessionSnapshot { mode: AuthMode; identity: ServerIdentity | null }

export type ServerRole = 'USER' | 'PREMIUM' | 'ADMIN';
export interface ServerIdentity { username: string; role: ServerRole }
export interface SessionSnapshot { identity: ServerIdentity | null }

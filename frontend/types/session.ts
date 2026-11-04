export type ServerRole = 'USER' | 'PREMIUM' | 'OWNER' | 'ADMIN';
export interface ServerIdentity { username: string; role: ServerRole }
export interface SessionSnapshot { identity: ServerIdentity | null }

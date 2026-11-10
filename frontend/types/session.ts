export type ServerRole = 'USER' | 'PREMIUM' | 'OWNER' | 'ADMIN';
export type ServerAccountStatus = 'ACTIVE' | 'WARNED' | 'SUSPENDED';
// status·warningReason은 경고 회원 상단 안내용이다(docs/admin-plan.md D8). 이전 세션 응답에는 없을 수 있다.
export interface ServerIdentity { username: string; role: ServerRole; status?: ServerAccountStatus | null; warningReason?: string | null }
export interface SessionSnapshot { identity: ServerIdentity | null }

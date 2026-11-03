'use client';
import styles from './DemoAccounts.module.css';
import { useState } from 'react';
import { useAuth } from '@/lib/state/authStore';
import { usePrototype } from '@/lib/state/prototypeStore';
import { demoAccounts, demoPassword, roleLabels } from '@/mocks/auth';
import { Button } from '@/components/common/Button';
export function DemoAccounts({ onSelected, showCredentials = false }: {
    onSelected?: () => void;
    showCredentials?: boolean;
}) { const { user, switchDemo } = useAuth(); const { notify } = usePrototype(); const [busy, setBusy] = useState(false); return <div className={[styles.scope, "auth-demos"].join(" ")}><div className={[styles.scope, "auth-divider"].join(" ")}><span>데모 계정으로 체험하기</span></div><div className={[styles.scope, "auth-demo-buttons"].join(" ")}>{demoAccounts.map(account => <Button disabled={busy} type="button" variant={user?.id === account.id ? 'primary' : 'secondary'} key={account.id} onClick={() => { setBusy(true); if (switchDemo(account.role)) {
    notify(`${roleLabels[account.role]} 계정으로 전환했습니다.`);
    onSelected?.();
} setBusy(false); }}>{roleLabels[account.role]}</Button>)}</div>{showCredentials && <details className={[styles.scope, "demo-credentials"].join(" ")}><summary>이메일로 로그인할 데모 계정 보기</summary><ul>{demoAccounts.map(a => <li key={a.id}>{roleLabels[a.role]}: <code>{a.email}</code></li>)}</ul><p>공통 데모 비밀번호: <code>{demoPassword}</code></p></details>}</div>; }

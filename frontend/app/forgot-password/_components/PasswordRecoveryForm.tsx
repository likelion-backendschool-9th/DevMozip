'use client';
import styles from './PasswordRecoveryForm.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { validEmail } from '@/lib/account/authService';
import { Input } from '@/components/common/Input';
import { Button } from '@/components/common/Button';
export function PasswordRecoveryForm() { const [email, setEmail] = useState(''); const [error, setError] = useState(''); const [done, setDone] = useState(false); return done ? <div className={[styles.scope, "recovery-done"].join(" ")} role="status"><strong>비밀번호 찾기 요청을 확인했습니다.</strong><p>실제 이메일은 발송하지 않았습니다. 이 화면은 복구 요청 흐름을 보여주는 데모이며, 비밀번호는 변경되지 않습니다.</p><Link className={[styles.scope, "button"].join(" ")} href="/login">로그인으로 돌아가기</Link><Button variant="secondary" onClick={() => { setDone(false); setEmail(''); }}>다시 입력</Button></div> : <form className={[styles.scope, "auth-form"].join(" ")} noValidate onSubmit={e => { e.preventDefault(); if (!validEmail(email)) {
    setError('올바른 이메일 주소를 입력해 주세요.');
    return;
} setDone(true); }}><label htmlFor="recovery-email">가입한 이메일</label><Input id="recovery-email" type="email" autoComplete="email" maxLength={254} value={email} onChange={e => { setEmail(e.target.value); setError(''); }} placeholder="you@example.com" aria-invalid={!!error}/>{error && <p className={[styles.scope, "form-error"].join(" ")} role="alert">{error}</p>}<Button type="submit" className={[styles.scope, "auth-submit"].join(" ")}>복구 안내 확인</Button><p className={[styles.scope, "auth-switch"].join(" ")}><Link href="/login">로그인으로 돌아가기</Link></p></form>; }

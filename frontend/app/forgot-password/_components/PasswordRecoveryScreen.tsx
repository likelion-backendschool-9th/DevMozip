'use client';
import { AuthShell } from '@/components/auth/AuthShell';
import { PasswordRecoveryForm } from '@/app/forgot-password/_components/PasswordRecoveryForm';
export function PasswordRecoveryPage(){return <AuthShell eyebrow="ACCOUNT RECOVERY" title="비밀번호를 잊으셨나요?" description="가입한 이메일을 입력해 복구 안내를 확인하세요."><PasswordRecoveryForm/></AuthShell>;}

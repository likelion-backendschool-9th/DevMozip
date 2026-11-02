'use client';
import { AuthShell } from '@/components/auth/AuthShell';
import { LoginForm } from '@/app/login/_components/LoginForm';
export function LoginPage(){return <AuthShell eyebrow="WELCOME BACK" title="다시 만나 반가워요" description="함께 만들 다음 프로젝트가 기다리고 있어요."><LoginForm/></AuthShell>;}

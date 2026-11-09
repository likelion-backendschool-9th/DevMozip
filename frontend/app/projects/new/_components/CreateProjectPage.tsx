'use client';
import styles from '../page.module.css';
import {useMockVisibility} from '@/lib/state/mockVisibilityStore';
import {ApiProjectForm} from '@/app/projects/_components/api/ApiProjectForm';
import { useState } from 'react';
import { PremiumProjectGate } from '@/components/projects/PremiumProjectGate';
import { Button } from '@/components/common/Button';
import Link from 'next/link';
import { LockKeyhole } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { canCreateProject } from '@/lib/projects/projectService';
import { CreateProjectForm } from '@/app/projects/_components/CreateProjectForm';
export default function CreateProjectPage({ apiEnabled = false }: { apiEnabled?: boolean }) {
    const { user } = useAuth();
    const [gate, setGate] = useState(false);
    const {enabled}=useMockVisibility();
    if(!enabled)return <ApiProjectForm/>;
    return (
        <div className={[styles.scope, 'container create-project-page'].join(' ')}>
            <Link className={[styles.scope, 'text-link'].join(' ')} href="/projects">
                ← 모집 목록으로
            </Link>
            {canCreateProject(user) ? (
                <section className={[styles.scope, 'create-project-panel'].join(' ')}>
                    <div className={[styles.scope, 'eyebrow'].join(' ')}>FIND YOUR NEXT TEAM</div>
                    <h1>함께할 동료를 모집해 보세요.</h1>
                    <p>핵심 목표와 모집 조건을 명확하게 알려주세요.</p>
                    <CreateProjectForm apiEnabled={apiEnabled} />
                </section>
            ) : (
                <section className={[styles.scope, 'planned-card premium-project-gate'].join(' ')}>
                    <LockKeyhole size={32} />
                    <h1>모집 공고 작성은 프리미엄 전용입니다.</h1>
                    <p>무료 회원도 모든 프로젝트를 조회하고 지원할 수 있습니다.</p>
                    {user ? (
                        <Link className={[styles.scope, 'button'].join(' ')} href="/subscription">
                            프리미엄 안내
                        </Link>
                    ) : (
                        <Link className={[styles.scope, 'button'].join(' ')} href="/login">
                            로그인하기
                        </Link>
                    )}
                    <Button variant="secondary" onClick={() => setGate(true)}>
                        프리미엄 권한 안내
                    </Button>
                    <p className={[styles.scope, 'auth-hint'].join(' ')}>
                        프리미엄 계정으로 로그인하면 작성 권한을 확인할 수 있습니다.
                    </p>
                </section>
            )}
            {gate && <PremiumProjectGate onClose={() => setGate(false)} />}
        </div>
    );
}

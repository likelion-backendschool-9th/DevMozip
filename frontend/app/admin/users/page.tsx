'use client';
import { useState } from 'react';
import Link from 'next/link';
import { adminStyles as styles } from '../_components/AdminShell';
import { useAuth } from '@/lib/state/authStore';
import { roleLabels } from '@/mocks/auth';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';

export default function AdminUsersPage() {
    const { publicUsers, administration, administer } = useAuth();
    const [query, setQuery] = useState('');
    const [member, setMember] = useState<{ id: string; status: 'active' | 'warned' | 'suspended' } | null>(null);
    const [reason, setReason] = useState('');
    const members = publicUsers.filter(u => (u.nickname + u.email + u.id).toLowerCase().includes(query.toLowerCase()));

    return (
        <>
            <section className={[styles.scope, 'governance-panel admin-main'].join(' ')} aria-label="회원 관리">
                <label className={[styles.scope, 'admin-search'].join(' ')}>
                    검색
                    <input
                        className={[styles.scope, 'form-input'].join(' ')}
                        value={query}
                        onChange={e => setQuery(e.target.value)}
                        placeholder="이름·이메일 또는 콘텐츠 검색"
                    />
                </label>
                <h2>
                    회원 목록 <small>{members.length}명</small>
                </h2>
                <p>경고는 안내 상태이며, 정지는 해당 계정의 로그인과 기능 사용을 제한합니다.</p>
                {members.map(u => (
                    <article className={[styles.scope, 'admin-row'].join(' ')} key={u.id}>
                        <div>
                            <Link href={`/users/${u.id}`}>{u.nickname}</Link>{' '}
                            <span className={[styles.scope, 'demo-pill'].join(' ')}>{roleLabels[u.role]}</span>
                            <small>
                                {u.email} ·{' '}
                                {administration.members[u.id]?.status === 'suspended'
                                    ? '정지'
                                    : administration.members[u.id]?.status === 'warned'
                                      ? '경고'
                                      : '정상'}
                            </small>
                            {administration.members[u.id]?.reason && (
                                <small>사유: {administration.members[u.id].reason}</small>
                            )}
                        </div>
                        <div className={[styles.scope, 'governance-actions'].join(' ')}>
                            {u.role !== 'admin' &&
                                (['warned', 'suspended', 'active'] as const).map((status, i) => (
                                    <Button
                                        variant="secondary"
                                        key={status}
                                        onClick={() => {
                                            setReason('');
                                            setMember({ id: u.id, status });
                                        }}
                                    >
                                        {['경고', '정지', '해제'][i]}
                                    </Button>
                                ))}
                        </div>
                    </article>
                ))}
                {!members.length && <p>검색 결과가 없습니다.</p>}
            </section>
            {member && (
                <Modal title="회원 상태 변경" onClose={() => setMember(null)}>
                    <p>
                        {publicUsers.find(u => u.id === member.id)?.nickname} ·{' '}
                        {member.status === 'suspended' ? '정지' : member.status === 'warned' ? '경고' : '해제'}
                    </p>
                    <label>
                        사유 (경고·정지 시 필수)
                        <textarea
                            className={[styles.scope, 'form-input'].join(' ')}
                            maxLength={300}
                            value={reason}
                            onChange={e => setReason(e.target.value)}
                        />
                    </label>
                    <Button
                        onClick={() => {
                            if (administer({ type: 'member', targetId: member.id, status: member.status, reason }))
                                setMember(null);
                        }}
                    >
                        변경 확인
                    </Button>
                </Modal>
            )}
        </>
    );
}

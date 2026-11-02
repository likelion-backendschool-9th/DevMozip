'use client';
import styles from './AboutTab.module.css';
import { ExternalLink, Pencil } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { Card } from '@/components/common/Card';
import { safeExternalUrl } from '@/lib/profiles/profileService';
import type { Profile } from '@/types/profile';
export function AboutTab({ profile, owner, onEdit }: {
    profile: Profile;
    owner: boolean;
    onEdit: () => void;
}) { return <div className={[styles.scope, "about-grid"].join(" ")}><Card className={[styles.scope, "profile-section about-intro"].join(" ")}><div className={[styles.scope, "tab-section-title"].join(" ")}><h2>반가워요, {profile.nickname}입니다.</h2>{owner && <Button variant="secondary" onClick={onEdit}><Pencil size={13}/>수정</Button>}</div><p className={[styles.scope, "preserve-text"].join(" ")}>{profile.introduction || '아직 상세 소개를 작성하지 않았습니다.'}</p></Card><Card className={[styles.scope, "profile-section"].join(" ")}><h2>기술 스택</h2><div className={[styles.scope, "profile-tags"].join(" ")}>{profile.stacks.map(s => <span key={s}>{s}</span>)}</div><h2 className={[styles.scope, "sub-heading"].join(" ")}>관심 분야</h2><div className={[styles.scope, "profile-tags cyan-tags"].join(" ")}>{profile.interests.map(s => <span key={s}>{s}</span>)}</div>{!!profile.projectInterests?.length && <><h2 className={[styles.scope, "sub-heading"].join(" ")}>관심 프로젝트 분야</h2><div className={[styles.scope, "profile-tags"].join(" ")}>{profile.projectInterests.map(s => <span key={s}>{s}</span>)}</div></>}</Card><Card className={[styles.scope, "profile-section profile-links"].join(" ")}><h2>더 알아보기</h2>{(['github', 'blog'] as const).map(key => profile[key] && safeExternalUrl(profile[key]) ? <a key={key} href={safeExternalUrl(profile[key])} target="_blank" rel="noreferrer"><span>{key === 'github' ? 'GitHub' : '블로그'}</span><span>{profile[key]}</span><ExternalLink size={14}/></a> : <p key={key}>{key === 'github' ? 'GitHub' : '블로그'} 링크 미등록</p>)}</Card></div>; }

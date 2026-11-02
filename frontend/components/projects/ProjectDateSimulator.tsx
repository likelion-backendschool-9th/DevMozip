'use client';
import styles from './ProjectDateSimulator.module.css';
import { useState } from 'react';
import { useProjects } from '@/lib/state/ProjectProvider';
import { Button } from '@/components/common/Button';
const format = (date: Date) => date.toLocaleString('sv-SE', { timeZone: 'Asia/Seoul' }).slice(0, 16).replace(' ', 'T');
export function ProjectDateSimulator() { const { state, now, simulate } = useProjects(); const [value, setValue] = useState(format(now)); const jump = (days: number) => { const next = new Date(now.getTime() + days * 86400000); setValue(format(next)); simulate(next.toISOString()); }; return <section className={[styles.scope, "project-date-simulator"].join(" ")}><div><strong>날짜 시뮬레이션 <span className={[styles.scope, "demo-pill"].join(" ")}>MOCK</span></strong><p>현재 기준 {now.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })} · {state.simulationDate ? '시뮬레이션 적용 중' : '실제 날짜'}</p><small>예정 종료일·리뷰/투표 기한·3일 재알림을 비교합니다. 실제 스케줄러는 없습니다. 생성 이력은 날짜를 되돌려도 유지됩니다.</small></div><form onSubmit={e => { e.preventDefault(); if (value)
    simulate(new Date(`${value}:00+09:00`).toISOString()); }}><label>시뮬레이션 날짜<input type="datetime-local" className={[styles.scope, "form-input"].join(" ")} required value={value} onChange={e => setValue(e.target.value)}/></label><Button type="submit" variant="secondary">날짜 적용</Button><Button type="button" variant="secondary" onClick={() => jump(3)}>+3일</Button><Button type="button" variant="secondary" onClick={() => { simulate(null); setValue(format(new Date())); }}>실제 날짜로</Button></form></section>; }

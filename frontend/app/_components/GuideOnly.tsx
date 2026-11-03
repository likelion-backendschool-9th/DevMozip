'use client';
import type { ReactNode } from 'react';
import { usePrototype } from '@/lib/state/prototypeStore';

/** 발표 안내 표시 설정(헤더의 안내 숨기기/보기)이 켜져 있을 때만 children을 보여 준다 */
export function GuideOnly({ children }: { children: ReactNode }) {
    const { settings } = usePrototype();
    return settings.showGuide ? children : null;
}

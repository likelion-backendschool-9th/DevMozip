import type { KakaoIntent, KakaoResult } from '@/types/auth';
import { safeNext } from './paths';

// 카카오 인증을 시작할 때 쿠키에 저장했다가 콜백에서 대조하는 값
export interface KakaoFlow { state: string; intent: KakaoIntent; next: string }

const intents: unknown[] = ['login', 'signup', 'link', 'unlink'] satisfies KakaoIntent[];
export const isKakaoIntent = (value: unknown): value is KakaoIntent => intents.includes(value);

export function serializeKakaoFlow(flow: KakaoFlow) {
    return JSON.stringify(flow);
}

export function parseKakaoFlow(value?: string): KakaoFlow | null {
    try {
        const flow = JSON.parse(value ?? 'null');
        if (typeof flow?.state !== 'string' || !flow.state || !isKakaoIntent(flow.intent)) return null;
        return { state: flow.state, intent: flow.intent, next: safeNext(flow.next) };
    } catch { return null; }
}

// 결과는 코드만 URL에 싣고 문구는 화면에서 정한다. 임의 문구를 URL로 주입할 수 없게 하기 위해서다.
export function kakaoResultPath(intent: KakaoIntent, result: KakaoResult) {
    const page = intent === 'login' ? '/login' : intent === 'signup' ? '/signup' : '/settings/privacy';
    return `${page}?kakao=${result}`;
}

const notices: Record<KakaoResult, { kind: 'success' | 'error'; message: string }> = {
    signup: { kind: 'success', message: '카카오 인증을 마쳤습니다. 가입 정보를 입력해 주세요.' },
    linked: { kind: 'success', message: '카카오 계정을 연동했습니다.' },
    unlinked: { kind: 'success', message: '카카오 연동을 해제했습니다.' },
    'already-registered': { kind: 'error', message: '이미 등록된 회원입니다.' },
    cancelled: { kind: 'error', message: '카카오 인증을 취소했습니다.' },
    'invalid-state': { kind: 'error', message: '카카오 인증 정보를 확인하지 못했습니다. 다시 시도해 주세요.' },
    failed: { kind: 'error', message: '카카오 인증을 완료하지 못했습니다. 다시 시도해 주세요.' },
    suspended: { kind: 'error', message: '이용이 정지된 계정입니다. 관리자에게 문의해 주세요.' },
    unavailable: { kind: 'error', message: '지금은 카카오 로그인을 사용할 수 없습니다.' },
    conflict: { kind: 'error', message: '이미 카카오 계정이 연동되어 있거나 다른 회원에 연동된 카카오 계정입니다.' },
    'unlink-blocked': { kind: 'error', message: '비밀번호를 설정했는지, 연동한 카카오 계정으로 인증했는지 확인해 주세요.' },
};

// 카카오 가입에서 이메일이 겹치면 자동으로 연결하지 않고 직접 연동하도록 안내한다.
export const KAKAO_DUPLICATE_EMAIL_MESSAGE = '이미 가입된 이메일입니다. 기존 계정으로 로그인한 뒤 개인정보 설정에서 카카오를 연동해 주세요.';

// URL의 코드가 목록에 없으면 아무것도 표시하지 않는다.
export function kakaoNotice(value: string | null) {
    return value && Object.hasOwn(notices, value) ? notices[value as KakaoResult] : null;
}

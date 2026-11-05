'use client';
import styles from './KakaoNotice.module.css';
import { useSearchParams } from 'next/navigation';
import { kakaoNotice } from '@/lib/auth/kakao';

// 카카오 인증을 마치고 돌아왔을 때 URL의 결과 코드에 맞는 안내를 보여 준다.
export function KakaoNotice() {
    const notice = kakaoNotice(useSearchParams().get('kakao'));
    if (!notice) return null;
    return notice.kind === 'error'
        ? <p className="form-error" role="alert">{notice.message}</p>
        : <p className={styles.success} role="status">{notice.message}</p>;
}

'use client';
import styles from './PasswordNotice.module.css';
import { useSearchParams } from 'next/navigation';
import { passwordNotice } from '@/lib/auth/passwordReset';

// 비밀번호를 재설정·변경하고 돌아왔을 때 URL의 결과 코드에 맞는 안내를 보여 준다.
export function PasswordNotice() {
    const notice = passwordNotice(useSearchParams().get('password'));
    return notice ? <p className={styles.success} role="status">{notice}</p> : null;
}

'use client';
import { AuthShell } from '@/app/_components/AuthShell';
import { useEffect, useState, useSyncExternalStore } from 'react';
import { readResetToken } from '@/lib/auth/passwordReset';
import { PasswordResetForm } from './_components/PasswordResetForm';
import { PasswordResetRequestForm } from './_components/PasswordResetRequestForm';

const subscribe = () => () => {};
const shell = { eyebrow: 'ACCOUNT RECOVERY', title: '비밀번호를 잊으셨나요?', description: '가입한 이메일로 비밀번호 재설정 링크를 보내 드립니다.' };

// 읽은 즉시 주소에서 지워 방문 기록과 화면 공유에 남지 않게 한다.
const removeTokenFromUrl = () => window.history.replaceState(null, '', window.location.pathname + window.location.search);

// 브라우저에서만 마운트되므로 첫 렌더링에서 fragment를 읽어도 서버 결과와 어긋나지 않는다.
function PasswordRecovery() {
    const [token, setToken] = useState(() => readResetToken(window.location.hash));
    useEffect(() => {
        if (readResetToken(window.location.hash)) removeTokenFromUrl();
        // 이 화면을 연 탭에 링크를 붙여 넣으면 fragment만 바뀌어 문서를 다시 불러오지 않는다.
        const onHashChange = () => {
            const found = readResetToken(window.location.hash);
            if (!found) return;
            removeTokenFromUrl();
            setToken(found);
        };
        window.addEventListener('hashchange', onHashChange);
        return () => window.removeEventListener('hashchange', onHashChange);
    }, []);
    return token ? (
        <AuthShell {...shell} title="새 비밀번호를 설정하세요" description="앞으로 로그인에 사용할 비밀번호를 입력해 주세요.">
            <PasswordResetForm key={token} token={token} onRestart={() => setToken(null)} />
        </AuthShell>
    ) : (
        <AuthShell {...shell}>
            <PasswordResetRequestForm />
        </AuthShell>
    );
}

export default function PasswordRecoveryPage() {
    const mounted = useSyncExternalStore(subscribe, () => true, () => false);
    // 토큰을 읽기 전에는 잘못된 폼이 보였다가 바뀌지 않도록 폼을 비워 둔다.
    return mounted ? <PasswordRecovery /> : <AuthShell {...shell}>{null}</AuthShell>;
}

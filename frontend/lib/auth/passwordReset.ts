// 메일 링크의 #token=... 에서 토큰을 꺼낸다. fragment는 서버로 전송되지 않아 접근 로그와 Referer에 남지 않는다.
export function readResetToken(hash: string) {
    return new URLSearchParams(hash.replace(/^#/, '')).get('token') || null;
}

const notices: Record<string, string> = {
    reset: '비밀번호를 재설정했습니다. 새 비밀번호로 로그인해 주세요.',
    changed: '비밀번호를 변경했습니다. 새 비밀번호로 로그인해 주세요.',
};

// 결과는 코드만 URL에 싣고 문구는 화면에서 정한다. 임의 문구를 URL로 주입할 수 없게 하기 위해서다.
export function passwordNotice(value: string | null) {
    return value && Object.hasOwn(notices, value) ? notices[value] : null;
}

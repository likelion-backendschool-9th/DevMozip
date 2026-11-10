// 서버 추천 엔진과 동일한 기술 별칭으로 요청 조건과 현재 공개 프로필을 대조한다.
const aliases: Record<string, string> = { 'react.js': 'react', reactjs: 'react', nodejs: 'node.js', nextjs: 'next.js', springboot: 'spring boot', postgres: 'postgresql' };
const skillKey = (value: string) => {
    const key = value.normalize('NFKC').trim().toLowerCase().replace(/\s+/g, ' ');
    return Object.hasOwn(aliases, key) ? aliases[key] : key;
};
export function developerReason(reason: string, skills: string[] = [], requestedSkills: string[] = []): string {
    const requested = new Set(requestedSkills.map(skillKey));
    const matching = [...new Map(skills.filter(value => requested.has(skillKey(value))).map(value => [skillKey(value), value.trim()])).values()];
    return publicReasonLines(reason).flatMap(line => {
        if (line.trim() === '모집 직군과 일치합니다.' || line.trim().startsWith('신뢰 온도')) return [];
        if (/^요청 기술 \d+개 중 \d+개가 일치합니다\.$/.test(line.trim()) || /^.+ 기술 스택을 활용할 수 있습니다\.$/.test(line.trim())) {
            if (!requested.size) return [];
            return matching.length ? [`제시하신 조건 중 ${matching.join(', ')} 기술 스택이 현재 프로필에 등록되어 있습니다.`]
                : [];
        }
        return [line];
    }).join('\n').trim();
}

// 저장된 계산 안내 중 자료 부재/내부 계산 설명만 숨긴다. 실제 구현 중·부분 부합 문장은 유지한다.
function publicReasonLines(reason: string): string[] {
    return reason.split('\n').map(line => line.trim()).filter(line => line && ![
        '세부 요구사항을 20% 반영했습니다.',
        '포트폴리오 연동 전 경험은 미산정이며',
        '비교 메시지가 없어 텍스트 항목을 제외하고',
        '조건과 일치하는 분야·기술·텍스트 정보가 부족합니다.',
    ].some(prefix => line.startsWith(prefix)));
}
export function projectReason(reason: string): string {
    const statusLabels: Record<string, string> = { RECRUITING: '모집 중', RECRUITMENT_COMPLETED: '모집 완료', IN_PROGRESS: '개발 진행 중', COMPLETED: '개발 완료' };
    return publicReasonLines(reason).map(line => {
        const match = /^모집·진행 상태: (RECRUITING|RECRUITMENT_COMPLETED|IN_PROGRESS|COMPLETED)$/.exec(line);
        return match ? `프로젝트 상태: ${statusLabels[match[1]]}` : line;
    }).join('\n');
}

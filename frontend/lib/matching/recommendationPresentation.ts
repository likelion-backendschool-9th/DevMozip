// 서버 추천 엔진과 동일한 기술 별칭으로 요청 조건과 현재 공개 프로필을 대조한다.
const aliases: Record<string, string> = { 'react.js': 'react', reactjs: 'react', nodejs: 'node.js', nextjs: 'next.js', springboot: 'spring boot', postgres: 'postgresql' };
const skillKey = (value: string) => {
    const key = value.normalize('NFKC').trim().toLowerCase().replace(/\s+/g, ' ');
    return Object.hasOwn(aliases, key) ? aliases[key] : key;
};
export function developerReason(reason: string, skills: string[] = [], requestedSkills: string[] = []): string {
    const requested = new Set(requestedSkills.map(skillKey));
    const matching = [...new Map(skills.filter(value => requested.has(skillKey(value))).map(value => [skillKey(value), value.trim()])).values()];
    return reason.split('\n').flatMap(line => {
        if (line.trim() === '모집 직군과 일치합니다.' || line.trim().startsWith('신뢰 온도')) return [];
        if (/^요청 기술 \d+개 중 \d+개가 일치합니다\.$/.test(line.trim()) || /^.+ 기술 스택을 활용할 수 있습니다\.$/.test(line.trim())) {
            if (!requested.size) return [];
            return matching.length ? [`제시하신 조건 중 ${matching.join(', ')} 기술 스택이 현재 프로필에 등록되어 있습니다.`]
                : ['현재 프로필에서 제시하신 기술 조건에 해당하는 스택을 확인할 수 없습니다.'];
        }
        return [line];
    }).join('\n').trim() || '저장된 추천 이유를 확인할 수 없습니다.';
}

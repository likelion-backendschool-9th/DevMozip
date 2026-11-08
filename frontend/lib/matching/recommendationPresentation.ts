// 이전 결과의 원본 스냅샷은 보존한다. 당시 기술명은 저장되지 않아 현재 프로필 정보임을 명시한다.
export function developerReason(reason: string, skills: string[] = []): string {
    return reason.split('\n').flatMap(line => {
        if (line.trim() === '모집 직군과 일치합니다.' || line.trim().startsWith('신뢰 온도')) return [];
        if (/^요청 기술 \d+개 중 \d+개가 일치합니다\.$/.test(line.trim())) {
            return skills.length ? [`현재 프로필에 ${skills.join(', ')} 기술 스택이 등록되어 있습니다.`] : [];
        }
        return [line];
    }).join('\n').trim() || '저장된 추천 이유를 확인할 수 없습니다.';
}

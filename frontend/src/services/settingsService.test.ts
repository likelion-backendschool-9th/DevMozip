import { describe, it, expect } from 'vitest';
import { parseSettings } from './settingsService';
describe('프로토타입 설정 복원', () => {
 it('이전 세션의 유효한 설정을 복원한다', () => { expect(parseSettings('{"version":1,"showGuide":false}')).toEqual({version:1,showGuide:false}); });
 it.each([null, '', '{broken', 'null', '[]', '{"version":2,"showGuide":false}', '{"version":1,"showGuide":"false"}'])('손상되거나 호환되지 않는 값 %s은 초기화한다', raw => { expect(parseSettings(raw)).toEqual({version:1,showGuide:true}); });
});

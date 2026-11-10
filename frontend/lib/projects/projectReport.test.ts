import {expect,it} from 'vitest';
import {projectReportError} from './projectReport';
import {reportReasons} from '@/mocks/project';
it('공고 신고 사유와 선택 상세 설명을 검증한다',()=>{for(const reason of reportReasons)expect(projectReportError(reason,'')).toBe('');expect(projectReportError('기타','설명')).toBe('');expect(projectReportError('','')).toContain('사유');expect(projectReportError('임의 사유','')).toContain('사유');expect(projectReportError('기타',123)).toContain('설명');expect(projectReportError('기타','가'.repeat(1001))).toContain('1,000');});

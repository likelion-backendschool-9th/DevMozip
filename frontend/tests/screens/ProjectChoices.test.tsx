import {describe,it,expect} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import {TagsInput} from '@/app/projects/_components/api/ApiCommon';
describe('공통 프로젝트 선택기',()=>{
 it('입력 전에도 기본 후보와 직접 입력 항목을 한 목록에 표시한다',()=>{const html=renderToStaticMarkup(<TagsInput label="모집 직군" options={['프론트엔드','백엔드']} values={['SRE']} onChange={()=>{}}/>);expect(html).toContain('프론트엔드');expect(html).toContain('백엔드');expect(html).toContain('aria-pressed="true"');expect(html.match(/>SRE<\/button>/g)?.length).toBe(1);expect(html).toContain('모집 직군 추가');});
});

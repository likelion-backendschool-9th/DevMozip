import {describe,it,expect,vi} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import {readdirSync,existsSync} from 'node:fs';
import path from 'node:path';
import {ClientReady} from '@/app/_components/ClientReady';
describe('브라우저 저장소와 Next 서버 경계',()=>{
 it('서버 렌더링에서는 저장소를 읽거나 초기값을 저장하지 않는다',()=>{const getItem=vi.fn(()=>{throw new Error('server storage access');});const setItem=vi.fn(()=>{throw new Error('server storage write');});vi.stubGlobal('localStorage',{getItem,setItem});vi.stubGlobal('sessionStorage',{getItem,setItem});try{const html=renderToStaticMarkup(<ClientReady><span>저장소를 복원한 이후 콘텐츠</span></ClientReady>);expect(html).toContain('DevMozip 화면을 준비하고 있어요');expect(html).not.toContain('저장소를 복원한 이후 콘텐츠');expect(getItem).not.toHaveBeenCalled();expect(setItem).not.toHaveBeenCalled();}finally{vi.unstubAllGlobals();}});
 it('컴포넌트 스타일은 해당 컴포넌트와 같은 디렉터리에 있다',()=>{const root=path.resolve(import.meta.dirname,'..');const walk=(dir:string):string[]=>readdirSync(dir,{withFileTypes:true}).flatMap(v=>v.isDirectory()?walk(path.join(dir,v.name)):[path.join(dir,v.name)]);const modules=[...walk(path.join(root,'app')),...walk(path.join(root,'components'))].filter(p=>p.endsWith('.module.css'));expect(modules.length).toBeGreaterThan(0);for(const file of modules)expect(existsSync(file.replace(/\.module\.css$/,'.tsx')),file).toBe(true);});
});

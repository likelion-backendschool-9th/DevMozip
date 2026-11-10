import {beforeEach,describe,expect,it,vi} from 'vitest';
vi.mock('@/lib/state/authStore',()=>({useAuth:()=>({user:null})}));
vi.mock('@/lib/state/prototypeStore',()=>({notify:vi.fn()}));
vi.mock('./useProjectApi',()=>({projectRequest:vi.fn(),ProjectApiError:class extends Error{constructor(message:string,public status:number){super(message);}}}));
import {projectRequest} from './useProjectApi';
import {loadProjectBookmarks,projectBookmarks,toggleProjectBookmark} from './projectBookmarkStore';
import type {ProjectListRecord} from '@/types/projectApi';
const request=vi.mocked(projectRequest),item={id:1} as ProjectListRecord;
beforeEach(()=>{vi.resetAllMocks();projectBookmarks.set({owner:'',items:[],ready:false,loading:false,pending:false,error:null});});
describe('실제 프로젝트 북마크 공통 상태',()=>{
 it('저장·해제 후 서버 결과를 모든 화면에 공유한다',async()=>{
  request.mockResolvedValueOnce([]);await loadProjectBookmarks('a');
  request.mockResolvedValueOnce({bookmarked:true}).mockResolvedValueOnce([item]);await toggleProjectBookmark('a',1);
  expect(request).toHaveBeenCalledWith('/1/bookmark','POST');expect(projectBookmarks.get().items).toEqual([item]);
  request.mockResolvedValueOnce({bookmarked:false}).mockResolvedValueOnce([]);await toggleProjectBookmark('a',1);
  expect(request).toHaveBeenCalledWith('/1/bookmark','DELETE');expect(projectBookmarks.get().items).toEqual([]);
 });
 it('계정을 바꾸면 이전 계정 응답과 저장 목록을 버린다',async()=>{
  let finish!:(items:ProjectListRecord[])=>void;
  request.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve as typeof finish;}));
  const old=loadProjectBookmarks('a');request.mockResolvedValueOnce([]);await loadProjectBookmarks('b');finish([item]);await old;
  expect(projectBookmarks.get().owner).toBe('b');expect(projectBookmarks.get().items).toEqual([]);
  await loadProjectBookmarks('');expect(projectBookmarks.get().owner).toBe('');
 });
 it('통신 오류에는 Mock으로 대체하지 않고 재시도한다',async()=>{
  request.mockRejectedValueOnce(new Error('offline'));expect(await loadProjectBookmarks('a')).toBe(false);
  expect(projectBookmarks.get().error?.status).toBe(502);expect(projectBookmarks.get().items).toEqual([]);
  request.mockResolvedValueOnce([item]);expect(await loadProjectBookmarks('a',true)).toBe(true);expect(projectBookmarks.get().error).toBeNull();
 });
 it('연속 클릭해도 진행 중인 저장을 중복 전송하지 않는다',async()=>{
  request.mockResolvedValueOnce([]);await loadProjectBookmarks('a');let finish!:(value:unknown)=>void;
  request.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve;})).mockResolvedValueOnce([item]);
  const first=toggleProjectBookmark('a',1);await toggleProjectBookmark('a',1);finish({});await first;
  expect(request.mock.calls.filter(call=>call[1]==='POST')).toHaveLength(1);
 });
});

import {applyKickAction} from '../../services/chatKickService';
import type {ChatKickAction} from '../../types/chatKick';
import {createContext,useContext,useEffect,useRef,useState,type ReactNode} from 'react';
import {projectHistory} from '../../services/projectSelectors';
import {syncProjectState} from '../../services/projectManagementService';
import {allProjects,applyRecruitmentAction,freshRecruitmentState,loadRecruitmentState,saveRecruitmentState} from '../../services/projectService';
import {validateReviewSubmission} from '../../services/projectReviewService';
import {useAuth} from './AuthProvider';
import {useProfiles} from './ProfileProvider';
import {usePrototype} from './PrototypeProvider';
import type {Collaboration} from '../../types/profile';
import type {TeamReviewInput} from '../../types/projectReview';
import type {RecruitmentAction,RecruitmentProject,RecruitmentState} from '../../types/project';
interface Result {ok:boolean;projectId?:string;error?:string}
interface ProjectContextValue {kick:(action:ChatKickAction)=>Result;state:RecruitmentState;now:Date;history:Collaboration[];projects:RecruitmentProject[];act:(action:RecruitmentAction)=>Result;submitReviews:(projectId:string,requestId:string,token:string,entries:TeamReviewInput[])=>Result;simulate:(date:string|null)=>void;reset:()=>void}
const Context=createContext<ProjectContextValue|null>(null);
export function ProjectProvider({children}:{children:ReactNode}){
 const {user,administration}=useAuth();const {state:profiles,act:profileAct}=useProfiles();const {notify}=usePrototype();
 const [state,setState]=useState(()=>{const loaded=loadRecruitmentState();return syncProjectState(loaded,loaded.simulationDate?new Date(loaded.simulationDate):new Date(),profiles);});
 const current=useRef(state);current.current=state;const source=useRef(profiles);source.current=profiles;
 const clock=()=>current.current.simulationDate?new Date(current.current.simulationDate):new Date();const now=clock();
 const commit=(next:RecruitmentState)=>{current.current=next;setState(next);return saveRecruitmentState(next);};
 const sync=()=>{const next=syncProjectState(current.current,clock(),source.current);if(JSON.stringify(next)!==JSON.stringify(current.current))commit(next);};
 useEffect(()=>{saveRecruitmentState(current.current);const timer=window.setInterval(sync,60000);return()=>window.clearInterval(timer);},[]);
 useEffect(()=>{sync();},[profiles.reviews]);
 const act=(action:RecruitmentAction):Result=>{try{const result=applyRecruitmentAction(current.current,action,user,source.current,clock());const saved=commit(result.state);if(action.type==='create'&&result.projectId)profileAct({type:'follow-publish',kind:'project',recordId:result.projectId,title:action.input.title});notify(saved?result.message:`${result.message} 저장 공간을 사용할 수 없어 이번 세션에만 유지됩니다.`);return {ok:true,projectId:result.projectId};}catch(e){const error=e instanceof Error?e.message:'요청을 처리하지 못했습니다.';notify(error);return {ok:false,error};}};
 const kick=(action:ChatKickAction):Result=>{try{const date=clock();const synced=syncProjectState(current.current,date,source.current);if(JSON.stringify(synced)!==JSON.stringify(current.current))commit(synced);const next=applyKickAction(current.current,action,user,source.current,date);const saved=commit(next);notify(saved?action.type==='start'?'강퇴 투표를 시작했습니다.':'투표를 반영했습니다.':'현재 세션에만 투표를 반영했습니다.');return {ok:true};}catch(e){const error=e instanceof Error?e.message:'투표를 처리하지 못했습니다.';notify(error);return {ok:false,error};}};
 const simulate=(date:string|null)=>{if(!user){notify('로그인 후 시뮬레이션을 이용해 주세요.');return;}if(date!==null&&!Number.isFinite(Date.parse(date))){notify('시뮬레이션 날짜를 확인해 주세요.');return;}const next=structuredClone(current.current);if(date)next.simulationDate=new Date(date).toISOString();else delete next.simulationDate;const saved=commit(syncProjectState(next,date?new Date(date):new Date(),source.current));notify(saved?'Mock 날짜 비교를 실행했습니다.':'현재 세션에만 날짜를 반영했습니다.');};
 const submitReviews=(projectId:string,requestId:string,token:string,entries:TeamReviewInput[]):Result=>{try{const date=clock();validateReviewSubmission(current.current,source.current,user?.id||null,projectId,requestId,token,entries,administration.keywords,date);if(!profileAct({type:'reviews-batch',projectId,entries},projectHistory(current.current),date))return {ok:false,error:'팀원 평가 내용을 확인해 주세요.'};const next=structuredClone(current.current);next.reviewRequests.find(r=>r.id===requestId)!.usedAt=date.toISOString();for(const n of next.notifications)if(n.reviewRequestId===requestId)n.resolvedAt=date.toISOString();const saved=commit(next);notify(saved?'팀원 리뷰를 모두 제출했습니다.':'리뷰는 현재 세션에 반영했지만 저장할 수 없습니다.');return {ok:true};}catch(e){const error=e instanceof Error?e.message:'리뷰를 제출하지 못했습니다.';notify(error);return {ok:false,error};}};
 return <Context.Provider value={{state,now,kick,projects:allProjects(state),history:projectHistory(state),act,simulate,submitReviews,reset:()=>{if(!commit(freshRecruitmentState()))notify('프로젝트 초기화는 이번 세션에만 유지됩니다.');}}}>{children}</Context.Provider>;
}
export function useProjects(){const v=useContext(Context);if(!v)throw new Error('ProjectProvider required');return v;}

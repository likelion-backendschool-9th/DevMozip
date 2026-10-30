import {canViewAccount} from '../../services/privacyService';
import {createContext,useContext,useRef,useState,type ReactNode} from 'react';
import {applyAdministration,effectiveUser,isSuspended,loadAdministration,saveAdministration,freshAdministration} from '../../services/administrationService';
import {authenticate,clearSession,createAccount,loadAccounts,loadSession,persistAccounts,persistSession,emailAvailable,nicknameAvailable} from '../../services/authService';
import {applyAccountArea,freshAccountArea,loadAccountArea,onboardingComplete,passwordChangeErrors,saveAccountArea} from '../../services/accountAreaService';
import {onboardingErrors} from '../../services/onboardingService';
import {demoAccounts} from '../../mocks/auth';
import {interestOptions} from '../../mocks/home';
import {usePrototype} from './PrototypeProvider';
import type {AdministrationState,AdministrationAction} from '../../types/administration';
import type {AccountAreaState,AccountAreaAction,OnboardingDraft,PasswordChangeInput} from '../../types/account';
import type {AccountRole,AuthUser,SignupInput} from '../../types/auth';
type PreferenceAction=Exclude<AccountAreaAction,{type:'onboarding-complete'}|{type:'password-confirmed'}>;
interface AuthContextValue {
 canViewProfile:(targetId:string)=>boolean;user:AuthUser|null;accountUser:AuthUser|null;publicUsers:AuthUser[];accountArea:AccountAreaState;needsOnboarding:boolean;
 administration:AdministrationState;administer:(action:AdministrationAction)=>boolean;resetAdministration:()=>void;
 accountAct:(action:PreferenceAction)=>boolean;completeOnboarding:(draft:OnboardingDraft)=>boolean;confirmPasswordChange:(input:PasswordChangeInput)=>Promise<void>;resetAccountArea:()=>void;
 login:(email:string,password:string,remember:boolean)=>Promise<void>;signup:(input:SignupInput)=>Promise<void>;logout:()=>void;switchDemo:(role:AccountRole|'guest')=>boolean;
 checkEmail:(email:string)=>boolean;checkNickname:(nickname:string)=>boolean;
}
const Context=createContext<AuthContextValue|null>(null);
export function AuthProvider({children}:{children:ReactNode}){
 const [accounts,setAccounts]=useState(loadAccounts);const [sessionUser,setUser]=useState(()=>loadSession(accounts));const {notify}=usePrototype();
 const [administration,setAdministration]=useState(loadAdministration);const [accountArea,setAccountArea]=useState(loadAccountArea);const areaRef=useRef(accountArea);areaRef.current=accountArea;
 const accountUser=sessionUser&&!isSuspended(administration,sessionUser.id)?effectiveUser(sessionUser,administration):null;
 const user=accountUser;
 const publicUsers=[...demoAccounts,...accounts.map(a=>a.user)].map(u=>effectiveUser(u,administration));
 const commitArea=(next:AccountAreaState)=>{areaRef.current=next;setAccountArea(next);if(!saveAccountArea(next))notify('변경했지만 저장 공간을 사용할 수 없어 현재 세션에만 유지됩니다.');};
 const accountAct=(action:PreferenceAction)=>{try{commitArea(applyAccountArea(areaRef.current,accountUser?.id||null,action));return true;}catch(e){notify(e instanceof Error?e.message:'변경 실패');return false;}};
 const administer=(action:AdministrationAction)=>{try{const next=applyAdministration(administration,user,action,publicUsers.map(u=>u.id));setAdministration(next);notify(saveAdministration(next)?'Mock 상태를 반영했습니다.':'변경했지만 저장할 수 없어 현재 세션에만 유지됩니다.');return true;}catch(e){notify(e instanceof Error?e.message:'변경 실패');return false;}};
 const start=(next:AuthUser,remember:boolean)=>{if(isSuspended(administration,next.id)){notify('정지된 회원입니다. 관리자 데모에서 해제해 주세요.');return false;}setUser(next);if(!persistSession(next,remember))notify('로그인되었지만 브라우저 저장 공간을 사용할 수 없어 현재 화면에서만 유지됩니다.');return true;};
 const logout=()=>{clearSession();setUser(null);};
 const login=async(email:string,password:string,remember:boolean)=>{const next=await authenticate(email,password,accounts);if(isSuspended(administration,next.id))throw new Error('정지된 회원입니다. 관리자에게 문의해 주세요.');start(next,remember);};
 const signup=async(input:SignupInput)=>{const account=await createAccount(input,accounts);const next=[...accounts,account];if(!persistAccounts(next))throw new Error('브라우저에 가입 정보를 저장하지 못했습니다. 저장 공간 설정을 확인해 주세요.');setAccounts(next);start(account.user,true);};
 const completeOnboarding=(draft:OnboardingDraft)=>{try{if(!user)throw new Error('로그인 후 이용해 주세요.');const errors=onboardingErrors(draft,3,publicUsers.some(u=>u.id!==user.id&&u.nickname.toLowerCase()===draft.nickname.trim().toLowerCase()));if(errors.length)throw new Error(errors[0]);const updated={...sessionUser!,nickname:draft.nickname.trim(),job:draft.jobs[0],career:draft.career,stacks:[...draft.stacks],interests:draft.interests.filter(i=>interestOptions.slice(1).includes(i)),available:draft.available,onboardingCompleted:true};const next=accounts.map(a=>a.user.id===updated.id?{...a,user:updated}:a);setAccounts(next);setUser(updated);if(!persistAccounts(next))notify('프로필은 반영했지만 계정 변경을 저장할 수 없어 현재 세션에만 유지됩니다.');commitArea(applyAccountArea(areaRef.current,user.id,{type:'onboarding-complete'}));return true;}catch(e){notify(e instanceof Error?e.message:'온보딩 완료 실패');return false;}};
 const confirmPasswordChange=async(input:PasswordChangeInput)=>{if(!user)throw new Error('로그인 후 이용해 주세요.');const errors=passwordChangeErrors(input);if(errors.length)throw new Error(errors[0]);await authenticate(user.email,input.currentPassword,accounts);commitArea(applyAccountArea(areaRef.current,user.id,{type:'password-confirmed'}));};
 const switchDemo=(role:AccountRole|'guest')=>{if(role==='guest'){logout();return true;}const demo=demoAccounts.find(a=>a.role===role);return demo?start(demo,true):false;};
 return <Context.Provider value={{canViewProfile:targetId=>canViewAccount(accountArea,targetId,user?.id||null),user,accountUser,publicUsers,accountArea,needsOnboarding:!!user&&!onboardingComplete(accountArea,user),accountAct,completeOnboarding,confirmPasswordChange,resetAccountArea:()=>commitArea(freshAccountArea()),administration,administer,resetAdministration:()=>{const next=freshAdministration();setAdministration(next);saveAdministration(next);},login,signup,logout,switchDemo,checkEmail:email=>emailAvailable(email,accounts),checkNickname:nickname=>nicknameAvailable(nickname,accounts)}}>{children}</Context.Provider>;
}
export function useAuth(){const value=useContext(Context);if(!value)throw new Error('AuthProvider required');return value;}

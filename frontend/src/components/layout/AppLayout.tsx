import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Bell, MessageSquare, ArrowUpRight, RotateCcw, Search, ChevronDown, LogOut, UserRound } from 'lucide-react';
import { navigation } from '../../mocks/routes';
import { routeTitle } from '../../services/routeService';
import { useAuth } from '../../app/providers/AuthProvider';
import { DemoAccounts } from '../../features/auth/DemoAccounts';
import { roleLabels } from '../../mocks/auth';
import { usePrototype } from '../../app/providers/PrototypeProvider';
import { useMessaging } from '../../app/providers/MessagingProvider';
import { useProjects } from '../../app/providers/ProjectProvider';
import { useProfiles } from '../../app/providers/ProfileProvider';
import { useHome } from '../../app/providers/HomeProvider';
import { Modal } from '../common/Modal';
import { Toast } from '../common/Toast';
import { Button } from '../common/Button';
import logo from '../../assets/devmozip-logo.svg';
export function AppLayout() {
 const {unreadCount,unreadMessages,reset:resetMessaging}=useMessaging();
 const { settings, toggleGuide, reset,notify } = usePrototype();const {reset:resetHome,resetAll:resetAllHome}=useHome();const {reset:resetProfiles,state:followState}=useProfiles();const {reset:resetProjects,state:projectState}=useProjects();const {user,logout,resetAdministration,administration,resetAccountArea,accountUser,needsOnboarding}=useAuth();
 const [resetOpen,setResetOpen]=useState(false);const [demoOpen,setDemoOpen]=useState(false);
 const navigate=useNavigate();const location=useLocation();const main=useRef<HTMLElement>(null);
 useEffect(()=>{window.scrollTo(0,0);main.current?.focus({preventScroll:true});document.title=routeTitle(location.pathname);},[location.pathname]);
 return <><a className="skip-link" href="#main">본문 바로가기</a><header className="header"><div className="header-inner"><Link className="logo" to="/" aria-label="DevMozip 홈"><img className="brand-logo" src={logo} alt="DevMozip" width="96" height="64"/></Link><nav aria-label="주 내비게이션">{navigation.map(item=><NavLink key={item.to} to={item.to}>{item.label}</NavLink>)}</nav><div className="header-actions"><NavLink className="icon-button header-search-link" aria-label="통합 검색" title="개발자·프로젝트 검색" to="/search?target=developer"><Search size={20}/></NavLink>{!user?<><Link to="/login" className="login-link">로그인</Link><Link to="/signup" className="button small">시작하기</Link></>:<><Link className="icon-button" aria-label="알림" to="/notifications"><Bell size={20}/>{unreadCount>0&&<span className="header-count" aria-label={`안 읽은 알림 ${unreadCount}개`}>{unreadCount>99?'99+':unreadCount}</span>}</Link><Link className="icon-button" aria-label="쪽지" to="/messages"><MessageSquare size={20}/>{unreadMessages>0&&<span className="header-count" aria-label={`안 읽은 쪽지 ${unreadMessages}개`}>{unreadMessages>99?'99+':unreadMessages}</span>}</Link><details className="profile-menu" key={location.pathname}><summary aria-label="프로필 메뉴"><span className="small-avatar lavender">{user.nickname.slice(0,2)}</span><ChevronDown size={12}/></summary><div><strong>{user.nickname} {user.role==='premium'&&<span className="premium-label">PRO</span>}</strong><Link to={`/users/${user.id}`}><UserRound size={15}/>내 프로필</Link><span className="auth-role-caption">{roleLabels[user.role]}</span>{user.role==='admin'&&<Link to="/admin">관리자 페이지</Link>}<Link to="/profile/me/likes">좋아요한 글</Link><Link to="/profile/me/bookmarks">내 북마크함</Link><Link to="/settings/account">개인정보 및 계정 설정</Link><Link to="/my-projects">내 프로젝트 관리</Link><Link to="/following">팔로워·팔로잉</Link><Link to="/matching">AI 추천 사용량</Link><Link to="/premium">프리미엄 안내</Link><button onClick={()=>{logout();resetHome();navigate('/');notify('로그아웃되었습니다.');}}><LogOut size={15}/>로그아웃</button></div></details></>}</div></div></header>
 <div className="utility-bar"><div className="utility-inner"><span><span className="status-dot"/>Mock 프로토타입 <b>· 프론트엔드 데모</b></span><div><button onClick={()=>setDemoOpen(true)}>데모 계정 전환</button><button onClick={toggleGuide}>{settings.showGuide?'안내 숨기기':'안내 보기'}</button><button onClick={()=>setResetOpen(true)}><RotateCcw size={13}/>데모 초기화</button></div></div></div>
 {user&&administration.members[user.id]?.status==='warned'&&<div className="member-warning" role="status">운영 안내 · {administration.members[user.id].reason}</div>}
 {projectState.simulationDate&&<div className="account-state-banner" role="status">프로젝트 날짜 시뮬레이션 적용 중 · {new Date(projectState.simulationDate).toLocaleString('ko-KR')} <Link to="/my-projects">날짜 변경/복원 →</Link></div>}{needsOnboarding&&<div className="account-state-banner" role="status">프로필 온보딩을 완료해 주세요. <Link to="/onboarding">이어서 작성 →</Link></div>}
 <main id="main" ref={main} tabIndex={-1}><Outlet/></main><footer><Link className="logo" to="/" aria-label="DevMozip 홈"><img className="brand-logo" src={logo} alt="DevMozip" width="96" height="64"/></Link><p>개발자의 다음 가능성을 연결합니다.</p><span>© 2026 DevMozip · Mock 기반 프로토타입</span><Link to="/premium">프리미엄 안내<ArrowUpRight size={14}/></Link></footer>
 {demoOpen&&<Modal title="데모 계정으로 둘러보기" onClose={()=>setDemoOpen(false)}><p>실제 인증 없이 일반 회원·프리미엄 회원·관리자를 체험합니다.</p><DemoAccounts onSelected={()=>setDemoOpen(false)}/>{user&&<Button variant="secondary" className="demo-logout" onClick={()=>{logout();resetHome();setDemoOpen(false);navigate('/');notify('로그아웃되었습니다.');}}>로그아웃 상태로 전환</Button>}</Modal>}
 {resetOpen&&<Modal title="데모 데이터를 초기화할까요?" onClose={()=>setResetOpen(false)}><p>계정 상태, 추천 입력·사용량과 안내 설정을 초기값으로 되돌립니다. 프로필·포트폴리오·글·리뷰·팔로우·모집 공고·지원·북마크·신고·대화·알림 읽음·구독·회원 제재·신고 처리·리뷰 선택지도 초기화하며 가입한 Mock 계정은 유지됩니다. 계정 공개 설정·카카오 Mock 연동·온보딩 임시 입력도 초기화합니다.</p><div className="modal-actions"><Button variant="secondary" onClick={()=>setResetOpen(false)}>취소</Button><Button onClick={()=>{logout();resetAllHome();resetProfiles();resetProjects();resetMessaging();resetAdministration();resetAccountArea();reset();setResetOpen(false);navigate('/');window.location.reload();}}>초기화</Button></div></Modal>}<Toast/></>;
}

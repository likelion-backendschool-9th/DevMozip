'use client';
import styles from './SearchBar.module.css';
import {useState} from 'react';
import {useNavigation as useNavigate,useCurrentLocation as useLocation} from '@/lib/navigation';
import {Search} from 'lucide-react';
import {developerSearchParams} from '@/lib/search/searchService';
import {Input} from '@/components/common/Input';
export function SearchBar({variant='header'}:{variant?:'header'|'page'}){
 const location=useLocation();const navigate=useNavigate();const urlQuery=location.pathname==='/explore'?new URLSearchParams(location.search).get('q')||'':'';
 const [query,setQuery]=useState(urlQuery);const urlKey=JSON.stringify([urlQuery,location.pathname]);const [shownUrl,setShownUrl]=useState(urlKey);
 if(shownUrl!==urlKey){setShownUrl(urlKey);setQuery(urlQuery);}
 return <form className={[styles.scope,variant==='header'?'header-search':'search-main-bar'].join(' ')} role="search" aria-label="개발자 검색" onSubmit={e=>{e.preventDefault();const next=developerSearchParams(new URLSearchParams(location.pathname==='/explore'?location.search:''));next.delete('page');if(query.trim())next.set('q',query.trim().slice(0,100));else next.delete('q');navigate('/explore?'+next);}}>
  <Input type="search" aria-label="개발자 검색어" placeholder="닉네임, 직군, 기술 스택으로 동료를 찾아보세요" value={query} onChange={e=>setQuery(e.target.value)} maxLength={100}/>
  <button className={[styles.scope,variant==='header'?'icon-button':'button'].join(' ')} aria-label="검색" type="submit"><Search size={variant==='header'?17:19}/>{variant==='page'&&'검색'}</button>
 </form>;
}

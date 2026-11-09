'use client';
import {useEffect,useRef,useState} from 'react';
import Link from 'next/link';
import {Check,Plus} from 'lucide-react';
import {Button} from '@/components/common/Button';
import {Input} from '@/components/common/Input';
import {normalizeProjectChoice,projectChoiceKey,validProjectChoice} from '@/lib/projects/projectChoices';
import type {ProjectApiError} from '@/lib/projects/useProjectApi';
import styles from './ApiCommon.module.css';
export function ApiError({error,retry}:{error:ProjectApiError;retry?:()=>void}){return <div className={styles.error} role="alert"><p>{error.message}</p>{error.status===401&&<Link href="/login">로그인하기</Link>}{retry&&<Button variant="secondary" onClick={retry}>다시 조회</Button>}</div>;}

/** 기본 후보·검색·직접 입력을 같은 선택 목록으로 관리한다. max=1은 단일 선택이다. */
export function TagsInput({label,values,onChange,options=[],max=5,compact=false,normalizeValue=normalizeProjectChoice,emptyLabel='전체'}:{label:string;values:string[];onChange:(values:string[])=>void;options?:readonly string[];max?:number;compact?:boolean;normalizeValue?:(value:string)=>string;emptyLabel?:string}){
 const picker=useRef<HTMLDetailsElement>(null);
 const [text,setText]=useState('');const [added,setAdded]=useState<string[]>([]);const [error,setError]=useState('');
 useEffect(()=>{if(!compact)return;const close=(event:PointerEvent)=>{if(picker.current&&!picker.current.contains(event.target as Node))picker.current.open=false;};document.addEventListener('pointerdown',close);return()=>document.removeEventListener('pointerdown',close);},[compact]);
 const choices=[...options,...added,...values].map(normalizeValue).filter((v,i,all)=>v&&all.findIndex(x=>projectChoiceKey(x)===projectChoiceKey(v))===i);
 const matches=choices.filter(v=>projectChoiceKey(v).includes(projectChoiceKey(text)));
 const selected=(value:string)=>values.some(v=>projectChoiceKey(normalizeValue(v))===projectChoiceKey(value));
 const choose=(value:string,toggle=true)=>{
  setError('');const normalized=normalizeValue(value);
  if(!validProjectChoice(normalized)){setError('1~30자로 입력해 주세요.');return;}
  const item=choices.find(v=>projectChoiceKey(v)===projectChoiceKey(normalized))??normalized;
  if(selected(item)){
   if(toggle)onChange(values.filter(v=>projectChoiceKey(normalizeValue(v))!==projectChoiceKey(item)));
   else setError('이미 선택한 항목입니다.');
   return;
  }
  if(max!==1&&values.length>=max){setError(`최대 ${max}개까지 선택할 수 있습니다.`);return;}
  if(!choices.some(v=>projectChoiceKey(v)===projectChoiceKey(item)))setAdded(prev=>[...prev,item]);
  onChange(max===1?[item]:[...values,item]);setText('');
  if(max===1&&picker.current)picker.current.open=false;
 };
 const editor=<div className={styles.tagEditor}>
  <label><span className={compact?'sr-only':''}>{label}</span><div className={styles.choiceSearch}><Input aria-label={label+' 입력'} value={text} maxLength={30} onChange={e=>{setText(e.target.value);setError('');}} onKeyDown={e=>{if(e.key==='Enter'&&!e.nativeEvent.isComposing){e.preventDefault();choose(text,false);}}} placeholder="기본 항목 검색 또는 직접 입력"/><Button type="button" variant="secondary" aria-label={label+' 추가'} disabled={!text.trim()} onClick={()=>choose(text,false)}><Plus size={16}/></Button></div></label>
  <div className={styles.choiceOptions} aria-label={label+' 기본 선택 목록'}>{matches.map(v=><button type="button" key={v} aria-pressed={selected(v)} onClick={()=>choose(v)}>{selected(v)&&<Check size={13} aria-hidden="true"/>}{v}</button>)}</div>
  {!matches.length&&<p className={styles.choiceHint}>목록에 없는 항목은 Enter 또는 +로 선택하세요.</p>}
  <span className={styles.choiceHint}>{values.length}/{max}개 선택 · {max===1?'한 항목 선택':'다시 누르면 선택 해제'}</span>
  {error&&<p className="form-error" role="alert">{error}</p>}
 </div>;
 return compact?<div className={styles.filterField}><span className={styles.filterLabel}>{label}</span><details ref={picker} className={styles.picker} onKeyDown={e=>{if(e.key==='Escape')e.currentTarget.open=false;}}><summary aria-label={label+' 선택'} title={values.join(' · ')}><span>{values.length?values.join(' · '):emptyLabel}</span></summary><div className={styles.pickerPanel}>{editor}</div></details></div>:editor;
}

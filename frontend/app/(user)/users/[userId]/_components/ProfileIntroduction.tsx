import {useMemo} from 'react';
import {introductionHtml} from '@/lib/profiles/profileIntroduction';
import styles from './ProfileIntroduction.module.css';
export function ProfileIntroduction({value,format='text'}:{value:string;format?:'text'|'html'}){const html=useMemo(()=>format==='html'?introductionHtml(value):'', [value,format]);if(!value.trim()||format==='html'&&!html.trim())return <p className={styles.empty}>아직 상세 소개를 작성하지 않았습니다.</p>;return format==='html'?<div className={styles.rich} data-introduction-format="html" dangerouslySetInnerHTML={{__html:html}}/>:<div className={styles.plain} data-introduction-format="text">{value}</div>;}

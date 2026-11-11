'use client';
import { uploadPortfolioFile } from '@/lib/portfolios/portfolioFiles';
import { PortfolioFileAttachments } from './PortfolioFileAttachments';
import {useEffect,useRef,useState} from 'react';
import Link from 'next/link';
import NextImage from 'next/image';
import {apiPortfolioErrors} from '@/lib/portfolios/portfolioDocument';
import {useParams} from 'next/navigation';
import {DndContext,PointerSensor,KeyboardSensor,useSensor,useSensors,closestCenter} from '@dnd-kit/core';
import {SortableContext,verticalListSortingStrategy,sortableKeyboardCoordinates} from '@dnd-kit/sortable';
import {Plus,ArrowLeft,Eye,PenLine} from 'lucide-react';
import {useAuth} from '@/lib/state/authStore';
import {useProfiles} from '@/lib/state/profileStore';
import {useNavigation} from '@/lib/navigation';
import {getPortfolioBlocks,movePortfolioBlock,portfolioBlockImages,portfolioBlockLinks,portfolioFields} from '@/lib/profiles/portfolioBlocks';
import {loadPortfolioDraft,savePortfolioDraft,clearPortfolioDraft,portfolioDraftKey} from '@/lib/profiles/portfolioDraft';
import {portfolioErrors} from '@/lib/profiles/profileService';
import {PortfolioCoverEditor} from './PortfolioCoverEditor';
import {createMockId} from '@/lib/utils/createMockId';
import {emptyPortfolio} from '@/mocks/profile';
import { stackOptions } from '@/mocks/home';
import {portfolioEditorPolicy,portfolioDragInstructions,portfolioFieldOptions} from '@/mocks/portfolioEditor';
import {PortfolioContent} from '@/app/portfolios/_components/PortfolioContent';
import {ProfileTagEditor} from '@/components/profile/ProfileTagEditor';
import {Input} from '@/components/common/Input';
import {Button} from '@/components/common/Button';
import {Modal} from '@/components/common/Modal';
import {PortfolioBlockEditor} from './PortfolioBlockEditor';
import type {Portfolio,PortfolioBlock,PortfolioInput} from '@/types/profile';
import styles from './PortfolioComposer.module.css';
export function PortfolioComposer({editing=false}:{editing?:boolean}){
 const {user}=useAuth();const {state}=useProfiles();const {portfolioId}=useParams<{portfolioId:string}>();const item=editing?state.portfolios.find(p=>p.id===portfolioId):undefined;
 if(!user)return <div className="container planned"><section className="planned-card"><h1>로그인하고 포트폴리오를 작성하세요.</h1><Link className="button" href="/login">로그인</Link></section></div>;
 if(editing&&(!item||item.ownerId!==user.id))return <div className="container planned"><section className="planned-card"><h1>이 포트폴리오를 수정할 수 없어요.</h1><p>본인이 작성한 포트폴리오만 수정할 수 있습니다.</p><Link className="button secondary" href={`/users/${user.id}?tab=portfolio`}>내 포트폴리오</Link></section></div>;
 return <PortfolioEditorForm key={`${user.id}:${item?.id||'new'}`} item={item} userId={user.id}/>;
}
export interface PortfolioEditorApi {
 initial:PortfolioInput; draftKey:string; visibility:'PUBLIC'|'PRIVATE'; canChangeVisibility:boolean;
 save:(input:PortfolioInput,visibility:'PUBLIC'|'PRIVATE',signal:AbortSignal)=>Promise<string>;
 saveDraft:(input:PortfolioInput,visibility:'PUBLIC'|'PRIVATE')=>boolean;
 backHref:string; reload?:()=>void; staleDraft?:boolean; restoredDraft?:boolean;
}
export function PortfolioEditorForm({item,userId,api}:{item?:Portfolio;userId:string;api?:PortfolioEditorApi}){
 const {act}=useProfiles();const navigate=useNavigation();const key=api?.draftKey||portfolioDraftKey(userId,item?.id);
 const [form,setForm]=useState<PortfolioInput>(()=>{if(api)return api.initial;const base=loadPortfolioDraft(key)||(item?{...item}:structuredClone(emptyPortfolio));return {...base,fields:portfolioFields(base),blocks:getPortfolioBlocks(base)};});
 const [restoredDraft]=useState(()=>!!api?.restoredDraft||(!api&&!!loadPortfolioDraft(key)));
 const [visibility,setVisibility]=useState<'PUBLIC'|'PRIVATE'>(api?.visibility||'PRIVATE');
 const [preview,setPreview]=useState(false);const [errors,setErrors]=useState<string[]>([]);const [status,setStatus]=useState(()=>restoredDraft?'이전에 임시저장한 내용을 불러왔어요. 이 브라우저에서 이어서 작성할 수 있습니다.':'작성 내용은 이 브라우저에 초안으로 자동 저장됩니다.');const [draftError,setDraftError]=useState('');const [blockBusy,setBlockBusy]=useState(0);const [coverBusy,setCoverBusy]=useState(false);const [remove,setRemove]=useState<string|null>(null);const [cancelOpen,setCancelOpen]=useState(false);const [saving,setSaving]=useState(false);
 const request=useRef<AbortController|null>(null);const submitting=useRef(false);
 const blocks=form.blocks!;const back=api?.backHref||(item?`/portfolios/${item.id}`:`/users/${userId}?tab=portfolio`);
 const sensors=useSensors(useSensor(PointerSensor,{activationConstraint:{distance:6}}),useSensor(KeyboardSensor,{coordinateGetter:sortableKeyboardCoordinates}));
 useEffect(()=>()=>request.current?.abort(),[]);
 useEffect(()=>{if(saving)return;const save=()=>api?api.saveDraft(form,visibility):savePortfolioDraft(key,form);const timer=window.setTimeout(()=>setStatus(save()?'초안 저장됨 · 이 브라우저에서만 유지됩니다.':'초안을 저장할 공간이 없습니다. 현재 화면의 입력은 유지됩니다.'),500);const onUnload=()=>{save();};window.addEventListener('beforeunload',onUnload);return()=>{window.clearTimeout(timer);window.removeEventListener('beforeunload',onUnload);};},[form,key,saving,api,visibility]);
 const saveDraftNow=()=>{
  if(saving||coverBusy||blockBusy>0)return false;
  const saved=api?api.saveDraft(form,visibility):savePortfolioDraft(key,form);
  const failure='임시저장하지 못했어요. 브라우저 저장 공간과 설정을 확인해 주세요. 입력은 유지됩니다.';
  setStatus(saved?'임시저장했습니다 · 이 브라우저에서만 유지됩니다.':failure);
  setDraftError(saved?'':failure);
  return saved;
 };
 const leaveWithDraft=()=>{if(saveDraftNow()){setSaving(true);navigate(back);}};
 const patch=(value:Partial<PortfolioInput>)=>{setForm(prev=>({...prev,...value}));setErrors([]);};
 const changeBlock=(id:string,value:Partial<PortfolioBlock>)=>{setForm(prev=>({...prev,blocks:prev.blocks!.map(b=>b.id===id?{...b,...value}:b)}));setErrors([]);};
 const move=(id:string,direction:number)=>{const index=blocks.findIndex(b=>b.id===id);const other=blocks[index+direction];if(other)patch({blocks:movePortfolioBlock(blocks,id,other.id)});};
 const add=(kind:PortfolioBlock['kind'])=>{if(blocks.length>=portfolioEditorPolicy.maxBlocks)return;const block:PortfolioBlock={id:createMockId(),kind,title:kind==='text'?'새 섹션':kind==='gallery'?'프로젝트 이미지':'프로젝트 링크',content:'',format:'markdown'};patch({blocks:[...blocks,block]});};
 const submit=async()=>{
  if(coverBusy||blockBusy>0||submitting.current)return;
  const errors=api?apiPortfolioErrors(form):portfolioErrors(form);setErrors(errors);
  if(errors.length){setPreview(false);requestAnimationFrame(()=>document.getElementById('portfolio-errors')?.focus());return;}
  if(!api){if(act({type:'portfolio-save',id:item?.id,input:form})){setSaving(true);clearPortfolioDraft(key);navigate(item?`/portfolios/${item.id}`:`/users/${userId}?tab=portfolio`);}else setErrors(['저장하지 못했습니다. 안내 메시지를 확인해 주세요.']);return;}
  submitting.current=true;setSaving(true);api.saveDraft(form,visibility);const controller=new AbortController();request.current=controller;
  try{const path=await api.save(form,visibility,controller.signal);if(!controller.signal.aborted){clearPortfolioDraft(key);navigate(path);}}
  catch(error){if(!controller.signal.aborted)setErrors([error instanceof Error?error.message:'저장하지 못했어요. 입력과 초안을 유지합니다.']);}
  finally{submitting.current=false;if(!controller.signal.aborted)setSaving(false);}
 };
 return <div className={`container ${styles.page}`}><header className={styles.topbar}><div><Link href={back} onClick={event=>{if(event.metaKey||event.ctrlKey||event.shiftKey||event.altKey)return;event.preventDefault();leaveWithDraft();}}><ArrowLeft size={15}/>돌아가기</Link><h1>{item?'포트폴리오 수정':'포트폴리오 작성'}</h1><p>기본 틀에서 시작하고, 나만의 이야기로 구성하세요.</p></div><div className={styles.actions}><Button type="button" variant="secondary" disabled={saving||coverBusy||blockBusy>0} onClick={saveDraftNow}>임시저장</Button><Button variant="secondary" disabled={saving} onClick={()=>setCancelOpen(true)}>취소</Button><Button variant="secondary" disabled={saving} onClick={()=>setPreview(!preview)}>{preview?<PenLine size={16}/>:<Eye size={16}/>} {preview?'편집으로':'전체 미리보기'}</Button><Button disabled={coverBusy||blockBusy>0||saving} onClick={submit}>{item?'변경사항 저장':'포트폴리오 등록'}</Button></div></header><p className={styles.status} role="status" aria-live="polite">{status}</p><p className="auth-hint">{restoredDraft&&'임시저장한 내용을 복원했어요. '}임시저장은 현재 기기와 브라우저에서만 유지됩니다. 포트폴리오 등록과는 별개입니다.</p>{api?.staleDraft&&<p role="alert">초안 작성 후 서버 내용이 변경되었습니다. 그대로 저장하면 충돌 안내가 표시됩니다.</p>}{api?.reload&&<Button variant="secondary" disabled={saving} onClick={api.reload}>초안 버리고 최신 내용 불러오기</Button>}<div className={styles.layout}><aside className={styles.outline}><strong>문서 구성</strong><a href="#portfolio-basics">기본 정보</a><div className={styles.outlineTitle}>내용 섹션 <span>{blocks.length}/20</span></div>{blocks.map((block,i)=><a href={preview?`#case-${block.id}`:`#edit-${block.id}`} key={block.id}>{String(i+1).padStart(2,'0')}　{block.title||'제목 없는 섹션'}</a>)}<p>핸들을 끌거나 위·아래 버튼으로 순서를 변경하세요. 제목·본문·섹션 구성을 자유롭게 수정할 수 있습니다.</p></aside><fieldset disabled={saving} className={styles.editor} style={{border:0,minWidth:0,padding:0,margin:0}} aria-label="포트폴리오 작성 내용">{errors.length>0&&<div id="portfolio-errors" role="alert" tabIndex={-1} className={styles.errors}><strong>입력 내용을 확인해 주세요.</strong><ul>{errors.map((error,i)=><li key={i}>{error}</li>)}</ul></div>}{preview?<div className={styles.preview}><span>저장 전 미리보기</span><h2>{form.title||'프로젝트 제목'}</h2><p>{form.summary||'한 줄 소개를 작성해 주세요.'}</p>{form.coverImage&&<NextImage unoptimized width={960} height={600} className={styles.previewCover} src={form.coverImage} alt="대표 이미지 미리보기"/>}<PortfolioContent item={form}/></div>:<><section className={styles.panel} id="portfolio-basics"><PortfolioCoverEditor upload={api?async file=>(await uploadPortfolioFile(file)).url:undefined} value={form.coverImage||''} onChange={coverImage=>patch({coverImage})} onBusy={setCoverBusy}/>{api&&<><label>대표 이미지 URL<Input value={form.coverImage||''} maxLength={2048} placeholder="https://..." onChange={e=>patch({coverImage:e.target.value})}/></label><label>공개 범위<select className="form-input" disabled={!api.canChangeVisibility} value={visibility} onChange={e=>setVisibility(e.target.value as 'PUBLIC'|'PRIVATE')}><option value="PRIVATE">비공개</option><option value="PUBLIC">공개</option></select></label><p className="auth-hint">이미지는 선택 즉시 서버에 업로드됩니다. 공개 범위는 변경사항 저장 시 함께 반영됩니다.</p></>}<h2>기본 정보</h2><p>목록에 보이는 요약과 프로젝트 개요를 작성하세요.</p><label>프로젝트명<Input value={form.title} maxLength={100} onChange={e=>patch({title:e.target.value})}/></label><label>한 줄 소개<Input value={form.summary} maxLength={200} onChange={e=>patch({summary:e.target.value})}/></label><div className={styles.twoColumns}><label>시작 월<Input type="month" value={form.start} onChange={e=>patch({start:e.target.value})}/></label><label>종료 월<Input type="month" value={form.end} min={form.start} onChange={e=>patch({end:e.target.value})}/></label><label>담당 역할<Input value={form.role} maxLength={100} onChange={e=>patch({role:e.target.value})}/></label><label>팀 구성<Input value={form.teamSize||''} maxLength={100} onChange={e=>patch({teamSize:e.target.value})}/></label></div><ProfileTagEditor label="분야" options={portfolioFieldOptions} value={form.fields||[]} onChange={fields=>patch({fields,field:fields[0]||''})}/><ProfileTagEditor label="기술 스택" options={stackOptions} value={form.stacks} onChange={stacks=>patch({stacks})}/></section>{api&&<PortfolioFileAttachments files={form.attachments||[]} onChange={attachments=>patch({attachments})} onBusy={busy=>setBlockBusy(count=>count+(busy?1:-1))}/>}<div className={styles.sectionHeading}><div><h2>자유롭게 구성하는 본문</h2><p>내용을 드래그해 배치하고, 제목과 형식을 바꿔 보세요.</p></div><span>{blocks.length}개 섹션</span></div><DndContext sensors={sensors} collisionDetection={closestCenter} accessibility={{screenReaderInstructions:portfolioDragInstructions}} onDragEnd={({active,over})=>{if(over&&active.id!==over.id)patch({blocks:movePortfolioBlock(blocks,String(active.id),String(over.id))});}}><SortableContext items={blocks.map(b=>b.id)} strategy={verticalListSortingStrategy}><div className={styles.blocks}>{blocks.map((block,index)=><PortfolioBlockEditor uploadImage={api?async file=>(await uploadPortfolioFile(file)).url:undefined} key={block.id} block={block} index={index} count={blocks.length} images={portfolioBlockImages(form,block)} links={portfolioBlockLinks(form,block)} onBusy={busy=>setBlockBusy(count=>count+(busy?1:-1))} onChange={value=>changeBlock(block.id,value)} onMove={direction=>move(block.id,direction)} onRemove={()=>setRemove(block.id)}/>)}</div></SortableContext></DndContext><div className={styles.addSections}><Button variant="secondary" disabled={blocks.length>=20} onClick={()=>add('text')}><Plus size={16}/>텍스트 섹션</Button><Button variant="secondary" disabled={blocks.length>=20} onClick={()=>add('gallery')}><Plus size={16}/>이미지 갤러리</Button><Button variant="secondary" disabled={blocks.length>=20} onClick={()=>add('links')}><Plus size={16}/>링크 모음</Button></div></>}</fieldset></div>{cancelOpen&&<Modal title="작성을 마칠까요?" onClose={()=>setCancelOpen(false)}><p>임시저장 후 나가면 이 브라우저에서 이어서 작성할 수 있습니다. 변경 취소를 선택하면 작성 중인 내용과 초안을 삭제합니다. 기존 포트폴리오는 유지됩니다.</p>{draftError&&<p role="alert">{draftError}</p>}<div className="modal-actions"><Button variant="secondary" onClick={()=>setCancelOpen(false)}>계속 작성</Button><Button type="button" variant="secondary" disabled={saving||coverBusy||blockBusy>0} onClick={leaveWithDraft}>임시저장 후 나가기</Button><Button onClick={()=>{setSaving(true);clearPortfolioDraft(key);navigate(back);}}>변경 취소</Button></div></Modal>}{remove&&<Modal title="이 섹션을 삭제할까요?" onClose={()=>setRemove(null)}><p>섹션 내용과 첨부 항목이 문서에서 제외됩니다. 변경사항을 저장하면 반영됩니다.</p><div className="modal-actions"><Button variant="secondary" onClick={()=>setRemove(null)}>유지</Button><Button onClick={()=>{patch({blocks:blocks.filter(b=>b.id!==remove)});setRemove(null);}}>섹션 삭제</Button></div></Modal>}</div>;
}

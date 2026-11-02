'use client';
import { useState } from 'react';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { socialReportReasons } from '@/mocks/social';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
export function ReportDialog({targetType,targetId,onClose}:{targetType:'post'|'comment';targetId:string;onClose:()=>void}){const {act}=useProfiles();const [reason,setReason]=useState(socialReportReasons[0]);return <Modal title={targetType==='post'?'게시글 신고':'댓글 신고'} onClose={onClose}><form className="profile-form" onSubmit={e=>{e.preventDefault();if(act({type:'social-report',targetType,targetId,reason}))onClose();}}><label>신고 사유<select className="form-input" value={reason} onChange={e=>setReason(e.target.value)}>{socialReportReasons.map(r=><option key={r}>{r}</option>)}</select></label><p className="auth-hint">같은 계정이 같은 내용에 1회 신고할 수 있는 임시 Mock 정책입니다. 실제 서버에는 전송하지 않습니다.</p><div className="modal-actions"><Button variant="secondary" type="button" onClick={onClose}>취소</Button><Button type="submit">신고 기록</Button></div></form></Modal>;}

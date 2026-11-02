'use client';
import { useState } from 'react';
import { useMessaging } from '@/lib/state/MessagingProvider';
import { userReportReasons } from '@/mocks/messaging';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
export function UserReportDialog({targetId,onClose}:{targetId:string;onClose:()=>void}){const {act}=useMessaging();const [reason,setReason]=useState(userReportReasons[0]);const [error,setError]=useState('');return <Modal title="사용자 신고" onClose={onClose}><form className="profile-form" onSubmit={e=>{e.preventDefault();const result=act({type:'report-user',targetId,reason});if(result.ok)onClose();else setError(result.error||'신고하지 못했습니다.');}}><label>신고 사유<select className="form-input" value={reason} onChange={e=>setReason(e.target.value)}>{userReportReasons.map(r=><option key={r}>{r}</option>)}</select></label><p className="auth-hint">계정당 사용자별 1회 신고하는 임시 Mock 정책입니다. 실제 관리자 서버에는 전송하지 않습니다.</p>{error&&<p role="alert" className="form-error">{error}</p>}<div className="modal-actions"><Button type="button" variant="secondary" onClick={onClose}>취소</Button><Button type="submit">신고 기록</Button></div></form></Modal>;}

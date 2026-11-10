'use client';
import {useEffect,useState} from 'react';
import {PortfolioDetailResult,type PortfolioDetailState} from '@/app/portfolios/_components/ApiPortfolioDetail';
import {PortfolioRequestError} from '@/lib/portfolios/portfolioApi';
import {fetchSubmittedPortfolio} from '@/lib/projects/submittedPortfolio';
export function SubmittedPortfolio({projectId,applicationId,snapshotId}:{projectId:string;applicationId:string;snapshotId:string}){
 const [attempt,setAttempt]=useState(0);const [result,setResult]=useState<PortfolioDetailState>({status:'loading'});
 useEffect(()=>{const controller=new AbortController();setResult({status:'loading'});
  void fetchSubmittedPortfolio(projectId,applicationId,snapshotId,controller.signal).then(value=>{if(!controller.signal.aborted)setResult({status:'ready',value});}).catch(error=>{if(!controller.signal.aborted)setResult({status:'error',code:error instanceof PortfolioRequestError?error.status:502,message:error instanceof Error?error.message:'제출본을 불러오지 못했습니다.'});});
  return()=>controller.abort();
 },[projectId,applicationId,snapshotId,attempt]);
 return <><p className="container auth-hint" role="status">지원 당시 보관된 제출본입니다. 원본 포트폴리오의 수정·삭제와 관계없이 유지되며 제출본은 수정할 수 없습니다.</p><PortfolioDetailResult result={result} onRetry={()=>setAttempt(v=>v+1)} backHref={`/projects/${projectId}`} backLabel="프로젝트로 돌아가기"/></>;
}

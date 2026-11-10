import { parsePortfolioDetail, PortfolioRequestError, validPortfolioId } from '@/lib/portfolios/portfolioApi';
export const validSubmissionIds = (...ids: string[]) => ids.every(validPortfolioId);
export async function fetchSubmittedPortfolio(projectId:string,applicationId:string,snapshotId:string,signal?:AbortSignal){
 if(!validSubmissionIds(projectId,applicationId,snapshotId))throw new PortfolioRequestError(400,'제출본 주소를 확인해 주세요.');
 const response=await fetch(`/api/projects/${projectId}/applications/${applicationId}/portfolios/${snapshotId}`,{cache:'no-store',signal});
 if(!response.ok)throw new PortfolioRequestError(response.status,response.status===403?'지원자는 자신의 제출본을, 프리미엄 주최자는 해당 공고의 제출본을 열람할 수 있습니다.':'제출 포트폴리오를 불러오지 못했습니다.');
 const value=parsePortfolioDetail(await response.json());
 return {...value,canEdit:false};
}

import {projectError,proxyProject} from '@/lib/projects/projectProxy';
import {parsePortfolioDetail} from '@/lib/portfolios/portfolioApi';
import {validSubmissionIds} from '@/lib/projects/submittedPortfolio';
export async function GET(_request:Request,{params}:{params:Promise<{projectId:string;applicationId:string;snapshotId:string}>}){
 const {projectId,applicationId,snapshotId}=await params;
 if(!validSubmissionIds(projectId,applicationId,snapshotId))return projectError(400,'제출본 주소를 확인해 주세요.');
 return proxyProject(`/api/v1/projects/${projectId}/applications/${applicationId}/portfolios/${snapshotId}`,{},body=>({...parsePortfolioDetail(body),canEdit:false}));
}

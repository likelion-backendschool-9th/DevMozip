import {SubmittedPortfolio} from './_components/SubmittedPortfolio';
export default async function SubmittedPortfolioPage({params}:{params:Promise<{projectId:string;applicationId:string;snapshotId:string}>}){
 const ids=await params;
 return <SubmittedPortfolio {...ids}/>;
}

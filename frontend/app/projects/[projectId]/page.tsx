import type {Metadata} from 'next';
import {ProjectDetailPage} from './_components/ProjectDetailScreen';
export const metadata:Metadata={title:'프로젝트 상세'};
export default function Page(){return <ProjectDetailPage/>;}

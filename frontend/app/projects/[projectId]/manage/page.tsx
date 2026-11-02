import type {Metadata} from 'next';
import {ProjectManagePage} from './_components/ProjectManageScreen';
export const metadata:Metadata={title:'프로젝트 관리'};
export default function Page(){return <ProjectManagePage/>;}

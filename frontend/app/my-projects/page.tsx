import type {Metadata} from 'next';
import {MyProjectsPage} from './_components/MyProjectsScreen';
export const metadata:Metadata={title:'내 프로젝트 관리'};
export default function Page(){return <MyProjectsPage/>;}

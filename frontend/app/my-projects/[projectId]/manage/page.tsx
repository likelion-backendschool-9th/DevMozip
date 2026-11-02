import type {Metadata} from 'next';
import {MyProjectManagePage} from './_components/MyProjectManageScreen';
export const metadata:Metadata={title:'내 프로젝트 워크스페이스'};
export default function Page(){return <MyProjectManagePage/>;}

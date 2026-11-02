import type {Metadata} from 'next';
import {AdminPage} from './_components/AdminScreen';
export const metadata:Metadata={title:'관리자'};
export default function Page(){return <AdminPage/>;}

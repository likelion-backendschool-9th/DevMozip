import type {Metadata} from 'next';
import {CreateProjectPage} from './_components/CreateProjectScreen';
export const metadata:Metadata={title:'모집 공고 작성'};
export default function Page(){return <CreateProjectPage/>;}

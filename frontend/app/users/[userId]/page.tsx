import type {Metadata} from 'next';
import {ProfilePage} from './_components/ProfileScreen';
export const metadata:Metadata={title:'개발자 프로필'};
export default function Page(){return <ProfilePage/>;}

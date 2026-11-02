import type {Metadata} from 'next';
import {FollowingPage} from './_components/FollowingScreen';
export const metadata:Metadata={title:'팔로잉'};
export default function Page(){return <FollowingPage/>;}

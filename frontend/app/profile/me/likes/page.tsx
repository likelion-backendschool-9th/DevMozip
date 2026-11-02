import type {Metadata} from 'next';
import {LikedPostsPage} from './_components/LikedPostsScreen';
export const metadata:Metadata={title:'좋아요한 글'};
export default function Page(){return <LikedPostsPage/>;}

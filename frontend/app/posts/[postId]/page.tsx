import type {Metadata} from 'next';
import {PostDetailPage} from './_components/PostDetailScreen';
export const metadata:Metadata={title:'게시글 상세'};
export default function Page(){return <PostDetailPage/>;}

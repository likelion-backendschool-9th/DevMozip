import type {Metadata} from 'next';
import {FeedPage} from './_components/FeedScreen';
export const metadata:Metadata={title:'SNS 피드'};
export default function Page(){return <FeedPage/>;}

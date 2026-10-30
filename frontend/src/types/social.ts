import type { PostCategory } from './home';
export interface PostImage { id:string;name:string;url:string;type:string;size:number }
export interface PostInput { title:string;body:string;category:PostCategory;tags:string[];images:PostImage[];github:string;external:string }
export interface PostComment { id:string;postId:string;authorId:string;body:string;createdAt:string;updatedAt?:string }
export interface SocialReport { id:string;targetType:'post'|'comment';targetId:string;reporterId:string;reason:string;createdAt:string }
export interface SocialState { likedAt?:Record<string,Record<string,string>>; likes:Record<string,string[]>;bookmarks:Record<string,string[]>;comments:PostComment[];reports:SocialReport[];views:Record<string,string[]> }
export type SocialAction = {type:'social-create';id:string;input:PostInput}|{type:'social-update';id:string;input:PostInput}|{type:'social-delete'|'social-like'|'social-bookmark';id:string}|{type:'social-view';id:string;visitorId:string}|{type:'social-comment-create';postId:string;body:string}|{type:'social-comment-edit';id:string;body:string}|{type:'social-comment-delete';id:string}|{type:'social-report';targetType:'post'|'comment';targetId:string;reason:string};
export type FeedSort='recommended'|'popular'|'views'|'latest';
export interface FeedFilters { mode:'recommended'|'following';category:string;q:string;sort:FeedSort }

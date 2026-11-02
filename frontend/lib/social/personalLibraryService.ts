import type {ProfileState} from '@/types/profile';
import type {RecruitmentProject,RecruitmentState} from '@/types/project';
export function likedPosts(state:ProfileState,userId:string){return state.posts.filter(p=>!p.hidden&&state.social.likes[p.id]?.includes(userId)).map(post=>({post,likedAt:state.social.likedAt?.[post.id]?.[userId]||null})).sort((a,b)=>(b.likedAt||'').localeCompare(a.likedAt||''));}
export function bookmarkedPosts(state:ProfileState,userId:string){return state.posts.filter(p=>!p.hidden&&state.social.bookmarks[userId]?.includes(p.id));}
export function bookmarkedProjects(projects:RecruitmentProject[],state:RecruitmentState,userId:string){return projects.filter(p=>state.bookmarks[userId]?.includes(p.id));}

export type SearchTarget = 'developer' | 'project';
export interface SearchFilters { interests?:string[];fields?:string[];target:SearchTarget; q:string; stacks:string[]; job:string; career:string; interest:string; available:''|'yes'|'no'; scope:'all'|'following'; role:string; field:string; status:string }

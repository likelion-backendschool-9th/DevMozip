import type {AccountAreaState} from '@/types/account';
/** 비공개는 소유자의 로그인이나 활동을 제한하지 않습니다. */
export function canViewAccount(state:AccountAreaState,targetId:string,viewerId:string|null){return targetId===viewerId||!state.accounts[targetId]?.isPrivate;}

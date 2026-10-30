export interface OnboardingDraft {nickname:string;bio:string;avatar:string;jobs:string[];career:string;stacks:string[];interests:string[];customFields:string[];available:boolean;projectInterests:string[];contact:string;github:string;blog:string}
export interface AccountPreferences {kakaoLinked:boolean;isPrivate:boolean;passwordConfirmedAt?:string;onboardingCompleted?:boolean;draft?:OnboardingDraft;step?:number}
export interface AccountAreaState {version:1;accounts:Record<string,AccountPreferences>}
export interface PasswordChangeInput {currentPassword:string;newPassword:string;confirmPassword:string}
export type AccountAreaAction={type:'kakao';linked:boolean}|{type:'privacy';isPrivate:boolean}|{type:'password-confirmed'}|{type:'onboarding-draft';draft:OnboardingDraft;step:number}|{type:'onboarding-complete'};

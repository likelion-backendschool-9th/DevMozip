import type {AccountAreaState} from '@/types/account';
export const initialAccountArea:AccountAreaState={version:1,accounts:{}};
export const accountMenu=[{key:'profile',label:'내 프로필'},{key:'likes',label:'좋아요한 글',path:'/profile/me/likes'},{key:'bookmarks',label:'내 북마크함',path:'/bookmarks'},{key:'settings',label:'개인정보 및 계정 설정',path:'/settings/privacy'}] as const;
export const onboardingSteps=['기본 정보','개발자 정보','협업 설정','최종 확인'];
export const avatarPolicy={maxBytes:256*1024,types:['image/png','image/jpeg','image/webp']};
export const mockPaymentMethod='데모 카드 •••• 4242 (실제 결제 수단 아님)';

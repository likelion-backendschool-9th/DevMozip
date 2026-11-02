import type {ProjectNotice,RecruitmentState} from '@/types/project';
import {reviewLink} from '@/lib/projects/projectReviewService';
export function projectNoticeHref(notice:ProjectNotice,state:RecruitmentState){if(notice.href)return notice.href;if(notice.title.includes('리뷰')){const request=state.reviewRequests.find(r=>r.projectId===notice.projectId&&r.recipientId===notice.recipientId);return request?reviewLink(request):`/my-projects/${notice.projectId}/manage`;}return `/projects/${notice.projectId}`;}

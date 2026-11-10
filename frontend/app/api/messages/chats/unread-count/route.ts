import { parseUnreadCount } from '@/lib/messaging/directChatApi';
import { proxyMessaging } from '@/lib/messaging/messagingProxy';

export async function GET() {
    return proxyMessaging('/api/v1/messages/chats/unread-count', {}, parseUnreadCount);
}

import type { Audience } from '@/domain/models';
import type { TimeProvider } from '@/domain/time';
import type { RentNestDb } from './db';

export async function addNotification(
  db: RentNestDb, time: TimeProvider, userId: number, audience: Audience, title: string, body: string, bookingId: number | null,
) {
  await db.notifications.add({ recipientUserId: userId, audience, title, body, bookingId, isRead: false, createdAt: time.nowMillis() });
}

export class NotificationRepository {
  constructor(private db: RentNestDb) {}

  private forUser(userId: number, audience: Audience) {
    return this.db.notifications.where('[recipientUserId+audience]').equals([userId, audience]);
  }

  async notifications(userId: number, audience: Audience) {
    return (await this.forUser(userId, audience).toArray()).sort((a, b) => b.createdAt - a.createdAt);
  }

  async unreadCount(userId: number, audience: Audience) {
    return (await this.forUser(userId, audience).toArray()).filter(n => !n.isRead).length;
  }

  async markAllRead(userId: number, audience: Audience) {
    await this.forUser(userId, audience).modify({ isRead: true });
  }
}

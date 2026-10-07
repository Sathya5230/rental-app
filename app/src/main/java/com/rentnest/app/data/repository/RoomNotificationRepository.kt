package com.rentnest.app.data.repository

import com.rentnest.app.data.local.AppDatabase
import com.rentnest.app.data.local.toDomain
import com.rentnest.app.domain.model.Audience
import com.rentnest.app.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomNotificationRepository @Inject constructor(db: AppDatabase) : NotificationRepository {
    private val dao = db.notificationDao()
    override fun notifications(userId: Long, audience: Audience) = dao.forUser(userId, audience).map { l -> l.map { it.toDomain() } }
    override fun unreadCount(userId: Long, audience: Audience) = dao.unreadCount(userId, audience)
    override suspend fun markAllRead(userId: Long, audience: Audience) = dao.markAllRead(userId, audience)
}

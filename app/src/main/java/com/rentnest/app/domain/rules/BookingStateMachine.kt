package com.rentnest.app.domain.rules

import com.rentnest.app.domain.DomainError
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.model.Booking
import com.rentnest.app.domain.model.BookingStatus
import com.rentnest.app.domain.model.BookingStatus.*

enum class BookingAction { ACCEPT, DECLINE, CANCEL, CHECK_OUT, RETURN }

object BookingStateMachine {
    private val transitions = mapOf(
        (REQUESTED to BookingAction.ACCEPT) to ACCEPTED,
        (REQUESTED to BookingAction.DECLINE) to DECLINED,
        (REQUESTED to BookingAction.CANCEL) to CANCELLED,
        (ACCEPTED to BookingAction.CANCEL) to CANCELLED,
        (ACCEPTED to BookingAction.CHECK_OUT) to ACTIVE,
        (ACTIVE to BookingAction.RETURN) to RETURNED,
    )

    fun next(from: BookingStatus, action: BookingAction): Outcome<BookingStatus> =
        transitions[from to action]?.let { Outcome.Success(it) }
            ?: Outcome.Failure(DomainError.InvalidTransition(from, action))

    fun canCancel(b: Booking): Boolean = (b.status to BookingAction.CANCEL) in transitions
    fun canReview(b: Booking): Boolean = b.status == RETURNED && !b.reviewed
}

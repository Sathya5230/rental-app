package com.rentnest.app.domain

import com.rentnest.app.domain.model.BookingStatus
import com.rentnest.app.domain.rules.BookingAction
import com.rentnest.app.domain.rules.ItemField

sealed interface DomainError {
    data object InvalidDateRange : DomainError
    data object DatesUnavailable : DomainError
    data object NoUnitFree : DomainError
    data object OwnListing : DomainError
    data object NotFound : DomainError
    data object UnitBusy : DomainError
    data object NotReviewable : DomainError
    data object InvalidRating : DomainError
    data class InvalidTransition(val from: BookingStatus, val action: BookingAction) : DomainError
    data class ValidationFailed(val fields: Set<ItemField>) : DomainError
}

fun DomainError.message(): String = when (this) {
    DomainError.InvalidDateRange -> "Please pick a valid date range starting today or later."
    DomainError.DatesUnavailable -> "Some of these dates are fully booked. Try a different range."
    DomainError.NoUnitFree -> "No unit is free for these dates."
    DomainError.OwnListing -> "You can't rent your own listing."
    DomainError.NotFound -> "We couldn't find that anymore."
    DomainError.UnitBusy -> "This unit has an active or upcoming booking. Return or reassign it first."
    DomainError.NotReviewable -> "This rental can't be reviewed."
    DomainError.InvalidRating -> "Choose a rating from 1 to 5 stars."
    is DomainError.InvalidTransition -> "That action isn't available for this booking anymore."
    is DomainError.ValidationFailed -> "Please fix the highlighted fields."
}

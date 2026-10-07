package com.rentnest.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object OnboardingRoute
@Serializable object LoginRoute
@Serializable object ChooseModeRoute
@Serializable object NotificationsRoute
@Serializable object ProfileRoute

// Customer
@Serializable object HomeRoute
@Serializable data class SearchRoute(val categoryId: Long = -1L, val providerId: Long = -1L)
@Serializable object RentalsRoute
@Serializable object SavedRoute
@Serializable data class ItemDetailsRoute(val itemId: Long)
@Serializable data class BookingDatesRoute(val itemId: Long)
@Serializable data class CheckoutRoute(val itemId: Long, val startEpochDay: Long, val endEpochDay: Long)
@Serializable data class BookingSuccessRoute(val bookingId: Long)

// Provider
@Serializable object DashboardRoute
@Serializable object InventoryRoute
@Serializable object ProviderBookingsRoute
@Serializable object EarningsRoute
@Serializable data class ItemEditorRoute(val itemId: Long = 0L)
@Serializable data class HandoverRoute(val bookingId: Long, val isReturn: Boolean)

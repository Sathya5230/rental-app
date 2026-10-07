package com.rentnest.app.domain

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: DomainError) : Outcome<Nothing>
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

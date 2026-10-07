package com.rentnest.app.domain.rules

import com.rentnest.app.domain.DomainError
import com.rentnest.app.domain.Outcome
import com.rentnest.app.domain.model.Item

enum class ItemField { TITLE, CATEGORY, DAILY_RATE, WEEKLY_RATE, DEPOSIT, THRESHOLD }

data class ItemDraft(
    val id: Long = 0,
    val providerId: Long,
    val title: String = "",
    val categoryId: Long? = null,
    val description: String = "",
    val photos: List<String> = emptyList(),
    val dailyRate: Long? = null,
    val weeklyRate: Long? = null,
    val deposit: Long? = 0,
    val specs: List<Pair<String, String>> = emptyList(),
    val lowStockThreshold: Int = 1,
    val isActive: Boolean = true,
)

object ItemValidator {
    fun validate(d: ItemDraft): Set<ItemField> = buildSet {
        if (d.title.isBlank()) add(ItemField.TITLE)
        if (d.categoryId == null) add(ItemField.CATEGORY)
        val daily = d.dailyRate
        if (daily == null || daily <= 0) add(ItemField.DAILY_RATE)
        val weekly = d.weeklyRate
        if (weekly == null || weekly <= 0 || (daily != null && daily > 0 && weekly > daily * 7)) add(ItemField.WEEKLY_RATE)
        val deposit = d.deposit
        if (deposit == null || deposit < 0) add(ItemField.DEPOSIT)
        if (d.lowStockThreshold < 0) add(ItemField.THRESHOLD)
    }

    fun toItem(d: ItemDraft): Outcome<Item> {
        val errors = validate(d)
        if (errors.isNotEmpty()) return Outcome.Failure(DomainError.ValidationFailed(errors))
        return Outcome.Success(
            Item(
                id = d.id,
                providerId = d.providerId,
                categoryId = d.categoryId!!,
                title = d.title.trim(),
                description = d.description.trim(),
                photos = d.photos,
                dailyRate = d.dailyRate!!,
                weeklyRate = d.weeklyRate!!,
                deposit = d.deposit!!,
                specs = d.specs.filter { it.first.isNotBlank() }.associate { it.first.trim() to it.second.trim() },
                lowStockThreshold = d.lowStockThreshold,
                isActive = d.isActive,
            ),
        )
    }

    fun fromItem(item: Item): ItemDraft = ItemDraft(
        id = item.id, providerId = item.providerId, title = item.title, categoryId = item.categoryId,
        description = item.description, photos = item.photos, dailyRate = item.dailyRate,
        weeklyRate = item.weeklyRate, deposit = item.deposit, specs = item.specs.toList(),
        lowStockThreshold = item.lowStockThreshold, isActive = item.isActive,
    )
}

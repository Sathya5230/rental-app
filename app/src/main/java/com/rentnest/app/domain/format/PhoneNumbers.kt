package com.rentnest.app.domain.format

/** Indian mobile numbers. Stored for display as "+91 98450 12001". */
object PhoneNumbers {
    /** The 10-digit national number, or null if [input] isn't a valid Indian mobile. */
    fun nationalDigits(input: String): String? {
        var d = input.filter(Char::isDigit)
        if (d.length == 12 && d.startsWith("91")) d = d.drop(2)
        if (d.length == 11 && d.startsWith("0")) d = d.drop(1)
        return d.takeIf { it.length == 10 && it[0] in '6'..'9' }
    }

    fun display(input: String): String? = nationalDigits(input)?.let { "+91 ${it.take(5)} ${it.drop(5)}" }

    /** E.164 form for sending an SMS, e.g. "+919845012001". */
    fun e164(input: String): String? = nationalDigits(input)?.let { "+91$it" }
}

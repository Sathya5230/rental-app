package com.rentnest.app.domain

interface SmsGateway {
    /** True when this device can send an SMS without the user's help (has telephony and permission). */
    fun canSendDirectly(): Boolean
    /** Sends [message] to [phone] in the background. False if it couldn't be sent. */
    fun send(phone: String, message: String): Boolean
}

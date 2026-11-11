package com.starfungames.paperboats.party

/** The port the big screen listens on, shown under its QR code. */
const val HOST_PORT = 8642

private val address = Regex("""(?:https?://)?([A-Za-z0-9.-]+)(?::(\d{1,5}))?/?""", RegexOption.IGNORE_CASE)

/**
 * Turns the address a player types from under the QR ("192.168.1.24:8642", with or without http://)
 * into the host's game socket, or null when it can't be an address.
 */
fun controllerSocketUrl(input: String): String? {
    val match = address.matchEntire(input.trim()) ?: return null
    val (host, portText) = match.destructured
    val port = if (portText.isEmpty()) HOST_PORT else portText.toInt()
    if (port !in 1..65_535) return null
    return "ws://$host:$port/ws"
}

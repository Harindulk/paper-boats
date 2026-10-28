package com.starfungames.paperboats

import java.net.Inet4Address
import java.net.NetworkInterface

/** The address phones on the same Wi-Fi can reach, or null when this machine has no LAN link. */
fun lanAddress(): String? = NetworkInterface.getNetworkInterfaces().toList()
    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
    .flatMap { it.inetAddresses.toList() }
    .filterIsInstance<Inet4Address>()
    .firstOrNull { it.isSiteLocalAddress }
    ?.hostAddress

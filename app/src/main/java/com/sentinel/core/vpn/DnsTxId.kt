package com.sentinel.core.vpn

import java.util.Random

/**
 * Picks a DNS transaction ID.
 *
 * The old code used a plain counter, so the IDs were 1, 2, 3 … — easy to guess. An attacker
 * who can guess the ID and the source port can forge a fake DNS answer (RFC 5452). The ID is
 * now random. Pass a [java.security.SecureRandom] in the app so the value cannot be predicted.
 *
 * Pure function, so the behaviour is unit-tested on the JVM.
 */
object DnsTxId {

    /** A random 0–65535 ID, avoiding ones already waiting for an answer when it easily can. */
    fun next(random: Random, inUse: Set<Short>): Short {
        var id = random.nextInt(0x10000).toShort()
        var tries = 0
        while (id in inUse && tries < 64) {
            id = random.nextInt(0x10000).toShort()
            tries++
        }
        return id
    }
}

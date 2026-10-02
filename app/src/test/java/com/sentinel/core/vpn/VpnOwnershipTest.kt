package com.sentinel.core.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Is the firewall running?" must mean this app's own VPN in this Android user. In a second
 * user the main user's VPN was taken for ours (seen on the emulator: u12 said "Firewall
 * Active" while only user 0's VPN existed), and rule changes then crashed the :vpn process.
 */
class VpnOwnershipTest {

    private val me = 10227                     // this app in user 0
    private val sameAppOtherUser = 1210227     // this app in user 12 (UID = user * 100000 + app id)
    private val invalid = -1                   // Process.INVALID_UID: owner hidden from other apps

    @Test fun `our own VPN counts`() = assertTrue(isOwnVpn(true, me, me))

    @Test fun `the same app's VPN in another user does not count`() {
        assertFalse(isOwnVpn(true, me, sameAppOtherUser))      // seen from user 12
        assertFalse(isOwnVpn(true, sameAppOtherUser, me))
    }

    @Test fun `a VPN whose owner Android hides from us does not count`() =
        assertFalse(isOwnVpn(true, invalid, me))

    @Test fun `a non-VPN network never counts`() = assertFalse(isOwnVpn(false, me, me))

    @Test fun `an unknown owner never matches, even if our UID were unknown too`() =
        assertFalse(isOwnVpn(true, invalid, invalid))
}

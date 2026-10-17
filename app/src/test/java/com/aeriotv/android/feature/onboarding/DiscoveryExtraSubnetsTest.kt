package com.aeriotv.android.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The build's extra discovery subnets: which hosts get asked. */
class DiscoveryExtraSubnetsTest {
    @Test
    fun aSlash24IsItsHostAddresses() {
        val hosts = DispatcharrDiscovery.extraSubnetHosts("192.168.2.0/24")
        assertEquals(254, hosts.size)
        assertEquals("192.168.2.1", hosts.first())
        assertEquals("192.168.2.254", hosts.last())
    }

    @Test
    fun widerPrefixesAreCappedAndBadEntriesSkipped() {
        assertEquals(254, DispatcharrDiscovery.extraSubnetHosts("10.0.5.9/16").size)
        assertEquals("10.0.5.1", DispatcharrDiscovery.extraSubnetHosts("10.0.5.9/16").first())
        assertTrue(DispatcharrDiscovery.extraSubnetHosts("").isEmpty())
        assertTrue(DispatcharrDiscovery.extraSubnetHosts("nonsense, 300.1.1.1/24").isEmpty())
        assertEquals(508, DispatcharrDiscovery.extraSubnetHosts("192.168.2.0/24, 192.168.3.0/24").size)
    }
}

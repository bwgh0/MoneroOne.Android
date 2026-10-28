package one.monero.moneroone.core.username

import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class UsernameConfigurationTest {
    private val key = "a".repeat(64)
    private val rules = "b".repeat(64)

    @Test fun releaseAcceptsOnlyTheProductionHostsOverHttps() {
        for (raw in listOf("https://registry.monero.one", "https://mirror.monero.one/oah/v1/", "https://Registry.Monero.One")) {
            assertTrue(raw, UsernameHosts.allows(URI(raw), debug = false))
        }
        for (raw in listOf("http://registry.monero.one", "https://registry.monero.one:8443", "https://staging-registry.monero.one",
            "http://localhost:8787", "https://mac.example.ts.net", "https://registry.monero.one.example.com",
            "https://user@registry.monero.one", "https://registry.monero.one?debug=1", "https://registry.monero.one#x")) {
            assertFalse(raw, UsernameHosts.allows(URI(raw), debug = false))
        }
    }

    @Test fun debugAcceptsThisMachineTailscaleAndStagingButNeverProduction() {
        for (raw in listOf("http://localhost:8787", "https://localhost:8787", "https://staging-registry.monero.one",
            "https://staging-mirror.monero.one/oah/v1/", "https://mac.example-tailnet.ts.net/oah")) {
            assertTrue(raw, UsernameHosts.allows(URI(raw), debug = true))
        }
        for (raw in listOf("https://registry.monero.one", "https://mirror.monero.one/oah/v1/", "http://staging-registry.monero.one",
            "https://staging-registry.monero.one:444", "http://mac.example.ts.net", "https://ts.net", "https://staging-registry.monero.one.example.com",
            "https://user@staging-registry.monero.one", "http://localhost:8787?debug=1")) {
            assertFalse(raw, UsernameHosts.allows(URI(raw), debug = true))
        }
    }

    @Test fun configurationKeepsTheTermsVersionAndTrimsTheUrl() {
        val release = UsernameConfiguration.from("https://registry.monero.one/", key, rules, "terms-pending-v1", debug = false)
        assertEquals(UsernameConfiguration("https://registry.monero.one", key, rules, "terms-pending-v1"), release)
        assertNotNull(UsernameConfiguration.from("https://staging-registry.monero.one", key, rules, "local-development-v1", debug = true))
    }

    @Test fun configurationRefusesBadKeysAndTermsVersions() {
        val url = "https://registry.monero.one"
        assertNull(UsernameConfiguration.from(url, "A".repeat(64), rules, "terms-pending-v1", debug = false))
        assertNull(UsernameConfiguration.from(url, key, "b".repeat(63), "terms-pending-v1", debug = false))
        assertNull(UsernameConfiguration.from(url, key, rules, "", debug = false))
        assertNull(UsernameConfiguration.from(url, key, rules, "terms pending", debug = false))
        assertNull(UsernameConfiguration.from(url, key, rules, "t".repeat(65), debug = false))
        assertNull(UsernameConfiguration.from("", key, rules, "terms-pending-v1", debug = false))
    }
}

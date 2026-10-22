package one.monero.moneroone.core.username

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

/** Headless JNI checks against the public wallet2 fixture, not UI automation. */
@RunWith(AndroidJUnit4::class)
class OahNativeTest {
    private val fixture = JSONObject(String(Base64.getDecoder().decode("eyJyZWNvcmQiOiAiVDBGSU1RRUZZV3hwWTJVQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBQUFBcXMtUHZWV19HaTJubTR0bXNZV2JwNEFISGdmSHpadzBYeUFNeUo2a21DYURVZWRQQnh1MXBTTDdlbmU2V2J5N05oT3NNWVdSa19Za1Z3MUdadVJDS0J3QWFaVXpVa3hDNk01bkxONlB0RWhObm53aUg3SnZvZHJReFFnWlJ4OTNXQ05qYTB5ZUsxOFpJU29QVmhpaktqdUhkMGtUdkRaYWU1alJxeU1aZzJXNFBJUFFBWmo2YVFUYzE0Mkg5ZC1XZ1NNYnFIZFp6bFA4cjZjM1VOY195blFOakx1T0V2elFXUUJQNl9abU9pTFFNRU16VnVqTlMwelZSTDNNMjlpYXJBZyIsICJoYXNoIjogIjI5ZDBkNmRjZDVkZDMxNTMzZGJiNjkyMzAwMDI0MTQ0YjgwNDViNzg5OGFlYzMxNDI3Y2E0OGU3YzdlYWY2MDciLCAib3duZXIiOiAiODlHZTZ5cHQ3QTJKaVA2d3R5RFZmV2VVOWF3dm56ckw0NHlpNjF3V0JhcFlUdUZkbUtRc2Y3dkRBaXhBQjhxem45Yk5vM0w3MzhoM3JQdnR6Rzg2eXhyNTFvd2dGdngiLCAia2V5IjogIjQ5YmRmYTY1MjU4MGU3ZDc1NjcyYmUzZDcwOGE4YTBmOWMzZTQyNGEzZWMwODgxODQwNmZiMzkyNWViYjAxN2UiLCAicnVsZXMiOiAib2FoL3J1bGVzL3YxXG5oYW5kbGUubWluX2xlbj0zXG5oYW5kbGUubWF4X2xlbj0xNVxuaGFuZGxlLmNoYXJzZXQ9YS16MC05X1xuaGFuZGxlLmZpcnN0PWEtelxuaGFuZGxlLm5vX2RvdWJsZV91bmRlcnNjb3JlPTFcbnNrZWxldG9uLmRyb3A9X1xuc2tlbGV0b24ubWFwPTBvMWxpbDVzXG5za2VsZXRvbi5yZXBsYWNlPXJuPm0sdnY+d1xuYmxvY2subWF0Y2g9bmFtZSxza2VsZXRvblxuZGlzcGxheS5jaGFyc2V0PTIwLTdlXG5kaXNwbGF5Lm1pbl9sZW49MVxuZGlzcGxheS5tYXhfbGVuPTMyXG5kaXNwbGF5LmJsb2NrX21hdGNoPWFsbnVtX3NrZWxldG9uXG5jbGFpbXMucGVyX2RldmljZT0xXG5jbGFpbXMucGVyaW9kX21vbnRocz0xMlxucm90YXRlLm1heF9wZXJfeWVhcj0xMlxuYmxvY2suY29udGFpbnM9YW5vbmVyb1xuYmxvY2suY29udGFpbnM9YmluYW5jZVxuYmxvY2suY29udGFpbnM9Yml0ZmluZXhcbmJsb2NrLmNvbnRhaW5zPWJpdHN0YW1wXG5ibG9jay5jb250YWlucz1ieWJpdFxuYmxvY2suY29udGFpbnM9Y2FrZXdhbGxldFxuYmxvY2suY29udGFpbnM9Y29pbmJhc2VcbmJsb2NrLmNvbnRhaW5zPWZlYXRoZXJ3YWxsZXRcbmJsb2NrLmNvbnRhaW5zPWdhdGVpb1xuYmxvY2suY29udGFpbnM9aHVvYmlcbmJsb2NrLmNvbnRhaW5zPWt1Y29pblxuYmxvY2suY29udGFpbnM9bWV0YW1hc2tcbmJsb2NrLmNvbnRhaW5zPW1vbmVyb1xuYmxvY2suY29udGFpbnM9bW9uZXJ1am9cbmJsb2NrLmNvbnRhaW5zPW15bW9uZXJvXG5ibG9jay5jb250YWlucz10cmFkZW9ncmVcbmJsb2NrLmNvbnRhaW5zPXRyZXpvclxuYmxvY2suZXhhY3Q9YWJ1c2VcbmJsb2NrLmV4YWN0PWFjY291bnRcbmJsb2NrLmV4YWN0PWFjY291bnRzXG5ibG9jay5leGFjdD1hZG1pblxuYmxvY2suZXhhY3Q9YWRtaW5pc3RyYXRvclxuYmxvY2suZXhhY3Q9YXBpXG5ibG9jay5leGFjdD1hcHBcbmJsb2NrLmV4YWN0PWFwcHNcbmJsb2NrLmV4YWN0PWFzc2V0c1xuYmxvY2suZXhhY3Q9YXV0aFxuYmxvY2suZXhhY3Q9YXV0b2NvbmZpZ1xuYmxvY2suZXhhY3Q9YXV0b2Rpc2NvdmVyXG5ibG9jay5leGFjdD1iZXRhXG5ibG9jay5leGFjdD1iaWxsaW5nXG5ibG9jay5leGFjdD1ibG9nXG5ibG9jay5leGFjdD1jZG5cbmJsb2NrLmV4YWN0PWNvbXBsaWFuY2VcbmJsb2NrLmV4YWN0PWNvbnRhY3RcbmJsb2NrLmV4YWN0PWRldlxuYmxvY2suZXhhY3Q9ZG5zXG5ibG9jay5leGFjdD1kb2NzXG5ibG9jay5leGFjdD1kb25hdGVcbmJsb2NrLmV4YWN0PWVwb2NoXG5ibG9jay5leGFjdD1leHBsb3JlclxuYmxvY2suZXhhY3Q9ZnRwXG5ibG9jay5leGFjdD1oZWxwXG5ibG9jay5leGFjdD1oZWxwZGVza1xuYmxvY2suZXhhY3Q9aG9zdG1hc3RlclxuYmxvY2suZXhhY3Q9aW1hcFxuYmxvY2suZXhhY3Q9aW5mb1xuYmxvY2suZXhhY3Q9bGVnYWxcbmJsb2NrLmV4YWN0PWxvZ1xuYmxvY2suZXhhY3Q9bG9naW5cbmJsb2NrLmV4YWN0PW1haWxcbmJsb2NrLmV4YWN0PW1haWxlcl9kYWVtb25cbmJsb2NrLmV4YWN0PW1pcnJvclxuYmxvY2suZXhhY3Q9bWlycm9yc1xuYmxvY2suZXhhY3Q9bW9kZXJhdG9yXG5ibG9jay5leGFjdD1ub2RlXG5ibG9jay5leGFjdD1ub2Rlc1xuYmxvY2suZXhhY3Q9bm9yZXBseVxuYmxvY2suZXhhY3Q9bm90aWZpY2F0aW9uc1xuYmxvY2suZXhhY3Q9b2ZmaWNpYWxcbmJsb2NrLmV4YWN0PW9uaW9uXG5ibG9jay5leGFjdD1vcGVyYXRvclxuYmxvY2suZXhhY3Q9cG9wXG5ibG9jay5leGFjdD1wb3AzXG5ibG9jay5leGFjdD1wb3N0bWFzdGVyXG5ibG9jay5leGFjdD1wcml2YWN5XG5ibG9jay5leGFjdD1yZWNvdmVyeVxuYmxvY2suZXhhY3Q9cmVmdW5kXG5ibG9jay5leGFjdD1yZWZ1bmRzXG5ibG9jay5leGFjdD1yZWdpc3RyYXJcbmJsb2NrLmV4YWN0PXJlZ2lzdHJ5XG5ibG9jay5leGFjdD1yZWxheVxuYmxvY2suZXhhY3Q9cm9vdFxuYmxvY2suZXhhY3Q9c2VjdXJpdHlcbmJsb2NrLmV4YWN0PXNtdHBcbmJsb2NrLmV4YWN0PXNuYXBzaG90XG5ibG9jay5leGFjdD1zbmFwc2hvdHNcbmJsb2NrLmV4YWN0PXN0YWZmXG5ibG9jay5leGFjdD1zdGFnaW5nXG5ibG9jay5leGFjdD1zdGF0aWNcbmJsb2NrLmV4YWN0PXN0YXR1c1xuYmxvY2suZXhhY3Q9c3Rld2FyZFxuYmxvY2suZXhhY3Q9c3VwcG9ydFxuYmxvY2suZXhhY3Q9c3lzYWRtaW5cbmJsb2NrLmV4YWN0PXN5c3RlbVxuYmxvY2suZXhhY3Q9dGVhbVxuYmxvY2suZXhhY3Q9dGVzdFxuYmxvY2suZXhhY3Q9dmVyaWZpZWRcbmJsb2NrLmV4YWN0PXZlcmlmeVxuYmxvY2suZXhhY3Q9d2VibWFpbFxuYmxvY2suZXhhY3Q9d2VibWFzdGVyXG5ibG9jay5leGFjdD13aXRuZXNzXG5ibG9jay5leGFjdD13d3dcbmJsb2NrLnByZWZpeD1hdG9taWNcbmJsb2NrLnByZWZpeD1ka2ltXG5ibG9jay5wcmVmaXg9ZWRnZVxuYmxvY2sucHJlZml4PWV4b2R1c1xuYmxvY2sucHJlZml4PWtyYWtlblxuYmxvY2sucHJlZml4PWxlZGdlclxuYmxvY2sucHJlZml4PXRydXN0XG4iLCAicnVsZXNIYXNoIjogImM2NWZmZDQxNjIxY2IyZGIyNmZmZTg3YTkwZTA3OTJhNjQ4NDM0ZGZiZmIwNzdjZDVlZDZkZjQ1ODlhMzA4ZDQifQ=="), Charsets.UTF_8))
    private fun request() = JSONObject().put("op", "verify_claim").put("record", fixture.getString("record"))
        .put("expected_record_hash", fixture.getString("hash")).put("handle", "alice")
        .put("subaddress", fixture.getString("owner")).put("network", "mainnet")
        .put("registrar_key", fixture.getString("key")).put("rules", fixture.getString("rules"))
        .put("rules_hash", fixture.getString("rulesHash"))
    @Test fun loadsNativeLibraryAndUsesCoreRules() {
        assertEquals("xmr_garden_club", OahCore.normalize("XMR_Garden_Club"))
        assertEquals("wwwwwwwwwwwwwww", OahCore.normalize("WWWWWWWWWWWWWWW"))
        for (invalid in listOf("ab", "abcdefghijklmnop", "a__b", "1abc", "admin", "joe@monero.one")) {
            try { OahCore.normalize(invalid); fail("Accepted invalid username") } catch (_: OahException) { }
        }
    }
    @Test fun acceptsRealWallet2GenesisAcrossJni() {
        assertEquals(fixture.getString("hash"), OahCore.call(request()).getString("genesis_hash"))
    }
    @Test fun rejectsDifferentRequestAndWallet() {
        for (field in listOf("expected_record_hash", "subaddress", "registrar_key", "rules_hash")) {
            try { OahCore.call(request().put(field, "00".repeat(32))); fail("Accepted substituted $field") }
            catch (_: OahException) { }
        }
    }
    @Test fun verifiesPaymentResolutionAndRejectsAlteredPublicationTime() {
        val text = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("username-resolution.json").bufferedReader().use { it.readText() }
        val request = JSONObject(text)
        val checked = OahCore.call(request)
        assertTrue(checked.getBoolean("published"))
        assertTrue(checked.getString("address").startsWith("8"))
        request.getJSONObject("request").getJSONObject("snapshot").put("published_at", 1_000_001)
        try { OahCore.call(request); fail("Accepted tampered publication time") } catch (_: OahException) { }
    }

}

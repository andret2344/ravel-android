package eu.andret.ravel.notify

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundRestrictionsTest {

    @Test
    fun `a manufacturer with a page gets its page, whatever the case it reports`() {
        assertEquals("https://dontkillmyapp.com/xiaomi", BackgroundRestrictions.helpUrl("Xiaomi"))
        assertEquals("https://dontkillmyapp.com/samsung", BackgroundRestrictions.helpUrl("samsung"))
        assertEquals("https://dontkillmyapp.com/huawei", BackgroundRestrictions.helpUrl("HUAWEI"))
    }

    @Test
    fun `brands sold under another manufacturer's name get that manufacturer's page`() {
        assertEquals("https://dontkillmyapp.com/xiaomi", BackgroundRestrictions.helpUrl("POCO"))
        assertEquals("https://dontkillmyapp.com/nokia", BackgroundRestrictions.helpUrl("HMD Global"))
    }

    @Test
    fun `a manufacturer without a page gets the page for everyone else, not a guessed address`() {
        assertEquals("https://dontkillmyapp.com/general", BackgroundRestrictions.helpUrl("Fairphone"))
        assertEquals("https://dontkillmyapp.com/general", BackgroundRestrictions.helpUrl("HONOR"))
        assertEquals("https://dontkillmyapp.com/general", BackgroundRestrictions.helpUrl(""))
    }

    @Test
    fun `the names the site lists for a page all lead to it`() {
        assertEquals("https://dontkillmyapp.com/google", BackgroundRestrictions.helpUrl("Google"))
        assertEquals("https://dontkillmyapp.com/ulefone", BackgroundRestrictions.helpUrl("Ulefone"))
    }
}

/*
 * Test for Library class
 */
package com.applinks.android

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue

class LibraryTest {
    @Test
    fun testSomeLibraryMethod() {
        val classUnderTest = Library()
        assertTrue(classUnderTest.someLibraryMethod(), "someLibraryMethod should return 'true'")
    }
}
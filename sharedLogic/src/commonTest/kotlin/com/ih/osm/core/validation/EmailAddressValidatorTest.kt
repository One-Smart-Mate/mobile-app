package com.ih.osm.core.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmailAddressValidatorTest {
    private val validator = EmailAddressValidator()

    @Test
    fun acceptsReportedAccountEmail() {
        assertTrue(validator.isValid("testuser1@gmail.com"))
    }

    @Test
    fun acceptsCommonValidAddresses() {
        assertTrue(validator.isValid("nombre.apellido+operaciones@sub.empresa.com"))
        assertTrue(validator.isValid("usuario_01@empresa.com.mx"))
    }

    @Test
    fun normalizesInvisibleCharactersAtTheEdges() {
        val rawValue = "\u200Btestuser1@gmail.com\u200E"
        assertEquals("testuser1@gmail.com", validator.normalize(rawValue))
        assertTrue(validator.isValid(rawValue))
    }

    @Test
    fun rejectsMalformedAddresses() {
        assertFalse(validator.isValid("testuser1@gmail"))
        assertFalse(validator.isValid("test user@gmail.com"))
        assertFalse(validator.isValid("test@@gmail.com"))
        assertFalse(validator.isValid("test@-gmail.com"))
    }
}

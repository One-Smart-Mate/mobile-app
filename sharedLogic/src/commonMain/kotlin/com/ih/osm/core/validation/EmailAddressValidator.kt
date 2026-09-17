package com.ih.osm.core.validation

class EmailAddressValidator {
    fun normalize(value: String): String = value.trim(::isEdgeIgnorable)

    fun isValid(value: String): Boolean {
        val email = normalize(value)
        if (email.isEmpty() || email.length > MAX_EMAIL_LENGTH) return false
        if (email.any(::isDisallowedCharacter)) return false

        val atIndex = email.indexOf('@')
        if (atIndex <= 0 || atIndex != email.lastIndexOf('@') || atIndex == email.lastIndex) {
            return false
        }

        val localPart = email.substring(0, atIndex)
        val domain = email.substring(atIndex + 1)
        if (localPart.length > MAX_LOCAL_PART_LENGTH) return false
        if (localPart.startsWith('.') || localPart.endsWith('.') || ".." in localPart) return false

        val labels = domain.split('.')
        if (labels.size < 2 || labels.last().length < MIN_TOP_LEVEL_DOMAIN_LENGTH) return false

        return labels.all { label ->
            label.isNotEmpty() &&
                label.length <= MAX_DOMAIN_LABEL_LENGTH &&
                !label.startsWith('-') &&
                !label.endsWith('-') &&
                label.all { character -> character.isLetterOrDigit() || character == '-' }
        }
    }

    private fun isEdgeIgnorable(character: Char): Boolean =
        character.isWhitespace() || character in INVISIBLE_FORMAT_CHARACTERS

    private fun isDisallowedCharacter(character: Char): Boolean =
        character.isWhitespace() ||
            character.code in CONTROL_CHARACTER_RANGE ||
            character in INVISIBLE_FORMAT_CHARACTERS

    private companion object {
        const val MAX_EMAIL_LENGTH = 254
        const val MAX_LOCAL_PART_LENGTH = 64
        const val MAX_DOMAIN_LABEL_LENGTH = 63
        const val MIN_TOP_LEVEL_DOMAIN_LENGTH = 2
        val CONTROL_CHARACTER_RANGE = 0x00..0x1F
        val INVISIBLE_FORMAT_CHARACTERS = setOf(
            '\u200B', // Zero-width space
            '\u200C', // Zero-width non-joiner
            '\u200D', // Zero-width joiner
            '\u200E', // Left-to-right mark
            '\u200F', // Right-to-left mark
            '\u2060', // Word joiner
            '\uFEFF', // Byte-order mark / zero-width no-break space
        )
    }
}

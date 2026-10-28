package com.focusmate.ai.model

data class ReelSignature(
    val creatorHandle: String = "",
    val audioTitle: String = "",
    val captionSnippet: String = "",
    val nodeFingerprint: String = "",
    val pageIndex: Int = -1
) {
    val key: String
        get() {
            val cleanCreator = creatorHandle.trim().lowercase().removePrefix("@")
            val cleanAudio = audioTitle.trim().lowercase()
                .replace("♪", "")
                .replace("♫", "")
                .trim()
            val cleanCaption = captionSnippet.trim().lowercase()
                .replace(Regex("\\s+"), " ")

            return when {
                cleanCreator.isNotEmpty() && cleanAudio.isNotEmpty() && cleanCaption.length >= 8 ->
                    "c:$cleanCreator|a:$cleanAudio|t:${cleanCaption.take(40)}"
                cleanCreator.isNotEmpty() && cleanAudio.isNotEmpty() -> "c:$cleanCreator|a:$cleanAudio"
                cleanCreator.isNotEmpty() -> "c:$cleanCreator"
                cleanAudio.isNotEmpty() -> "a:$cleanAudio"
                cleanCaption.length >= 8 -> "txt:${cleanCaption.take(40)}"
                nodeFingerprint.isNotBlank() -> "id:${nodeFingerprint.trim().lowercase()}"
                else -> ""
            }
        }

    val isValid: Boolean
        get() = key.isNotBlank()

    /** Multi-field identity is strong enough to preserve a brief Reel seen between rapid updates. */
    val hasStrongContentIdentity: Boolean
        get() {
            val hasCreator = creatorHandle.isNotBlank()
            val hasAudio = audioTitle.isNotBlank()
            val hasCaption = captionSnippet.trim().length >= 12
            return (hasCreator && (hasAudio || hasCaption)) || (hasAudio && captionSnippet.trim().length >= 20)
        }
}

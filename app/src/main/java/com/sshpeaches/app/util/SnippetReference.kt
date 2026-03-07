package com.majordaftapps.sshpeaches.app.util

private const val SNIPPET_REF_PREFIX = "snippet:"

fun snippetReference(snippetId: String): String = "$SNIPPET_REF_PREFIX$snippetId"

fun parseSnippetReference(value: String): String? =
    value.takeIf { it.startsWith(SNIPPET_REF_PREFIX) }?.removePrefix(SNIPPET_REF_PREFIX)


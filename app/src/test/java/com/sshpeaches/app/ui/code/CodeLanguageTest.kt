package com.majordaftapps.sshpeaches.app.ui.code

import org.junit.Assert.assertEquals
import org.junit.Test

class CodeLanguageTest {

    @Test
    fun picksTheGrammarFromTheFileName() {
        mapOf(
            "/etc/nginx/nginx.conf" to CodeLanguage.INI,
            "/home/me/deploy.sh" to CodeLanguage.SHELL,
            "/home/me/.bashrc" to CodeLanguage.SHELL,
            "/srv/app/.env" to CodeLanguage.SHELL,
            "docker-compose.yml" to CodeLanguage.YAML,
            "values.YAML" to CodeLanguage.YAML,
            "package.json" to CodeLanguage.JSON,
            "/etc/systemd/system/app.service" to CodeLanguage.INI,
            "/etc/ssh/sshd_config" to CodeLanguage.INI,
            "manage.py" to CodeLanguage.PYTHON,
            "index.ts" to CodeLanguage.JAVASCRIPT,
            "pom.xml" to CodeLanguage.XML,
            "README.md" to CodeLanguage.MARKDOWN,
            "Dockerfile" to CodeLanguage.DOCKERFILE,
            "Dockerfile.prod" to CodeLanguage.DOCKERFILE,
            "notes.txt" to CodeLanguage.PLAIN
        ).forEach { (name, expected) -> assertEquals(name, expected, CodeLanguage.detect(name)) }
    }

    @Test
    fun fallsBackToTheShebang() {
        assertEquals(CodeLanguage.SHELL, CodeLanguage.detect("/usr/local/bin/backup", "#!/usr/bin/env bash\nset -e"))
        assertEquals(CodeLanguage.SHELL, CodeLanguage.detect("run", "#!/bin/sh"))
        assertEquals(CodeLanguage.PYTHON, CodeLanguage.detect("tool", "#!/usr/bin/env python3"))
        assertEquals(CodeLanguage.JAVASCRIPT, CodeLanguage.detect("cli", "#!/usr/bin/env node"))
        assertEquals(CodeLanguage.PLAIN, CodeLanguage.detect("hosts", "127.0.0.1 localhost"))
    }

    @Test
    fun everyGrammarIsListedInTheBundledIndex() {
        val index = java.io.File("src/main/assets/textmate/languages.json").takeIf { it.exists() }
            ?: java.io.File("app/src/main/assets/textmate/languages.json")
        val text = index.readText()
        CodeLanguage.entries.mapNotNull { it.scopeName }.forEach { scope ->
            assert(text.contains("\"scopeName\": \"$scope\"")) { "$scope missing from languages.json" }
        }
    }
}

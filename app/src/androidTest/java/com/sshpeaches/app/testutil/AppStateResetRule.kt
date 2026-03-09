package com.majordaftapps.sshpeaches.app.testutil

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class AppStateResetRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
                AppStateResetter.reset(context)
                base.evaluate()
            }
        }
    }
}

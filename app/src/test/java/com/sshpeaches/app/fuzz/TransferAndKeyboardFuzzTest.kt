package com.majordaftapps.sshpeaches.app.fuzz

import com.majordaftapps.sshpeaches.app.ui.decodeTransferPayloadEnvelope
import com.majordaftapps.sshpeaches.app.ui.encodeTransferPayloadEnvelope
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardActionType
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardLayoutDefaults
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardModifier
import com.majordaftapps.sshpeaches.app.ui.keyboard.KeyboardSlotAction
import java.io.ByteArrayOutputStream
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.Base64
import java.util.zip.GZIPOutputStream
import kotlin.random.Random
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings/backup transfer envelope, the export keyboard-layout JSON, and terminal profiles.
 * The private JSON helpers in SSHPeachesRoot.kt are reached through reflection.
 */
class TransferAndKeyboardFuzzTest {

    private val rootKt: Class<*> = Class.forName("com.majordaftapps.sshpeaches.app.ui.SSHPeachesRootKt")

    private fun method(name: String, vararg types: Class<*>): Method =
        rootKt.getDeclaredMethod(name, *types).apply { isAccessible = true }

    private fun <T> call(m: Method, vararg args: Any?): T = try {
        @Suppress("UNCHECKED_CAST")
        m.invoke(null, *args) as T
    } catch (e: InvocationTargetException) {
        throw e.targetException
    }

    private val keyboardLayoutFromJson by lazy { method("keyboardLayoutFromJson", JSONArray::class.java) }
    private val keyboardLayoutToJson by lazy { method("keyboardLayoutToJson", List::class.java) }
    private val terminalProfilesFromJson by lazy { method("terminalProfilesFromJson", JSONArray::class.java) }
    private val requiresPassphrase by lazy { method("transferPayloadRequiresPassphrase", String::class.java) }

    // ---------------- envelope ----------------

    @Test
    fun envelope_roundTrip_forRandomJsonText() {
        val random = Random(Fuzz.DEFAULT_SEED + 40)
        repeat(2_000) {
            val repeated = Fuzz.randomValidUnicode(random, 30).repeat(random.nextInt(1, 200))
            val payload = "{\"v\":2,\"notes\":" + org.json.JSONObject.quote(repeated) + "}"
            val encoded = encodeTransferPayloadEnvelope(payload)
            assertEquals(payload, decodeTransferPayloadEnvelope(encoded))
            // Files may arrive with whitespace / BOM around the base64.
            assertEquals(payload, decodeTransferPayloadEnvelope("﻿  $encoded\r\n"))
        }
    }

    @Test
    fun envelope_mutations_neverThrowOrHang() {
        val random = Random(Fuzz.DEFAULT_SEED + 41)
        val sample = encodeTransferPayloadEnvelope("{\"v\":2,\"hosts\":[" + "{\"id\":\"h\",\"pwdPayload\":\"x\"},".repeat(200) + "{}]}")
        repeat(3_000) {
            val input = if (random.nextInt(5) == 0) Fuzz.randomString(random, 80) else Fuzz.mutateBase64(random, sample)
            Fuzz.guarded("decodeTransferPayloadEnvelope", input) { decodeTransferPayloadEnvelope(input) }
            Fuzz.guarded("transferPayloadRequiresPassphrase", input) { call<Boolean>(requiresPassphrase, input) }
        }
    }

    @Test
    fun envelope_gzipBomb_isBounded() {
        val decompressedSize = 64 * 1024 * 1024
        val compressed = ByteArrayOutputStream().use { out ->
            GZIPOutputStream(out).use { gz ->
                gz.write("{\"v\":2,\"notes\":\"".toByteArray())
                val chunk = ByteArray(1 shl 20) { 'a'.code.toByte() }
                repeat(decompressedSize / chunk.size) { gz.write(chunk) }
                gz.write("\"}".toByteArray())
            }
            out.toByteArray()
        }
        val encoded = Base64.getEncoder().encodeToString(compressed)
        val decoded = decodeTransferPayloadEnvelope(encoded)
        println("gzip bomb: ${encoded.length} chars of input -> ${decoded?.length} chars decoded")
        assertTrue("a ${encoded.length}-char payload decoded to ${decoded?.length} chars", (decoded?.length ?: 0) < 16 * 1024 * 1024)
    }

    // ---------------- keyboard layout ----------------

    private fun randomSlot(random: Random): KeyboardSlotAction = KeyboardSlotAction(
        type = KeyboardActionType.entries.random(random),
        label = Fuzz.randomValidUnicode(random, 8),
        text = Fuzz.randomValidUnicode(random, 8),
        keyCode = if (random.nextBoolean()) null else random.nextInt(1, 300),
        modifier = if (random.nextBoolean()) null else KeyboardModifier.entries.random(random),
        sequence = Fuzz.randomValidUnicode(random, 6),
        ctrl = random.nextBoolean(), alt = random.nextBoolean(), shift = random.nextBoolean(),
        repeatable = random.nextBoolean(),
        iconId = if (random.nextBoolean()) "" else listOf("up", "down", "fn", "keyboard", "paste", "x").random(random)
    )

    @Test
    fun keyboard_normalizeIsIdempotent_andExportImportRoundTrips() {
        val random = Random(Fuzz.DEFAULT_SEED + 42)
        repeat(1_000) {
            val slots = List(random.nextInt(0, 40)) { randomSlot(random) }
            val normalized = KeyboardLayoutDefaults.normalizeSlots(slots)
            assertEquals(KeyboardLayoutDefaults.SLOT_COUNT, normalized.size)
            assertEquals(normalized, KeyboardLayoutDefaults.normalizeSlots(normalized))
            val json: JSONArray = call(keyboardLayoutToJson, slots)
            val reparsed: List<KeyboardSlotAction> = call(keyboardLayoutFromJson, JSONArray(json.toString()))
            assertEquals("slots=$slots", normalized, reparsed)
            KeyboardLayoutDefaults.replaceSwipeNavWithFn(normalized)
        }
    }

    @Test
    fun keyboard_garbageJson_neverThrows() {
        val random = Random(Fuzz.DEFAULT_SEED + 43)
        val sample = (call<JSONArray>(keyboardLayoutToJson, KeyboardLayoutDefaults.DEFAULT_SLOTS)).toString()
        repeat(3_000) {
            val text = Fuzz.mutateString(random, sample)
            val array = runCatching { JSONArray(text) }.getOrNull() ?: return@repeat
            val parsed: List<KeyboardSlotAction> = Fuzz.guarded("keyboardLayoutFromJson", text) { call(keyboardLayoutFromJson, array) }
            assertEquals(KeyboardLayoutDefaults.SLOT_COUNT, parsed.size)
        }
        listOf("[]", "[1,2,3]", "[null]", "[\"esc\",\"ctrl-c\"]", "[{\"type\":\"KEY\",\"keyCode\":\"abc\"}]", "[{\"keyCode\":1e30}]")
            .forEach { text -> Fuzz.guarded("keyboardLayoutFromJson", text) { call<List<*>>(keyboardLayoutFromJson, JSONArray(text)) } }
        repeat(2_000) {
            val token = Fuzz.randomString(random, 10)
            Fuzz.guarded("legacyStringToAction", token) { KeyboardLayoutDefaults.legacyStringToAction(token) }
            Fuzz.guarded("combinationAction", token) { KeyboardLayoutDefaults.combinationAction(token, ctrl = true) }
        }
    }

    @Test
    fun terminalProfiles_garbageJson_neverThrows_andValuesClamped() {
        val random = Random(Fuzz.DEFAULT_SEED + 44)
        val sample = """[{"id":"p1","name":"P","font":"X","fontSizeSp":12,"foregroundHex":"#FFFFFF","backgroundHex":"#000000","cursorHex":"#FF0000","cursorStyle":"BAR","cursorBlink":false,"ansiColors":["#000000","#111111","#222222","#333333","#444444","#555555","#666666","#777777","#888888","#999999","#AAAAAA","#BBBBBB","#CCCCCC","#DDDDDD","#EEEEEE","#FFFFFF"]}]"""
        repeat(3_000) {
            val text = Fuzz.mutateString(random, sample)
            val array = runCatching { JSONArray(text) }.getOrNull() ?: return@repeat
            val profiles: List<com.majordaftapps.sshpeaches.app.data.model.TerminalProfile> =
                Fuzz.guarded("terminalProfilesFromJson", text) { call(terminalProfilesFromJson, array) }
            profiles.forEach {
                assertTrue(it.fontSizeSp in 6..28)
                assertTrue(it.id.isNotBlank())
                assertEquals(16, it.ansiColors.size)
            }
        }
    }
}

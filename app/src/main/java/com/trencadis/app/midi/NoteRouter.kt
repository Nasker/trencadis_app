package com.trencadis.app.midi

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.trencadis.app.sync.NoteDestination

/**
 * Single-threaded note dispatcher.
 *
 * All note events (add/remove destinations, note-on, note-off) are queued on a
 * dedicated looper so off/on pairs are emitted in the correct order and so the
 * gate-length scheduled note-off cannot race with the next note-on.
 */
class NoteRouter {

    private val noteThread = HandlerThread("note-router").apply { start() }
    private val handler = Handler(noteThread.looper)

    private val destinations = mutableListOf<NoteDestination>()
    private var lastPitch = -1
    private var lastChannel = 1
    private var pendingOff: Runnable? = null
    private var pendingOffPitch = -1

    private inline fun runOnNoteThread(crossinline block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) {
            block()
        } else {
            handler.post { block() }
        }
    }

    fun add(destination: NoteDestination) = runOnNoteThread {
        if (!destinations.contains(destination)) destinations.add(destination)
    }

    fun remove(destination: NoteDestination) = runOnNoteThread {
        destinations.remove(destination)
    }

    /**
     * Sends [pitch] on. If [durationMs] is positive, a matching note-off is
     * scheduled after that many milliseconds; the next note-on cancels any
     * pending off and releases the previous note before the new one.
     */
    fun noteOn(pitch: Int, velocity: Int, channel: Int = 1, durationMs: Long = -1) =
        runOnNoteThread { doNoteOn(pitch, velocity, channel, durationMs) }

    private fun doNoteOn(pitch: Int, velocity: Int, channel: Int, durationMs: Long) {
        // Cancel a previously scheduled note-off before retriggering.
        pendingOff?.let { handler.removeCallbacks(it) }
        pendingOff = null
        pendingOffPitch = -1

        // Release the previous note first — including same-pitch repeats.
        // Re-sending note-on without a note-off stacks voices on the receiver,
        // and the eventual single note-off leaves one instance hanging forever.
        if (lastPitch >= 0) doNoteOff(lastPitch, lastChannel)

        lastPitch = pitch
        lastChannel = channel
        destinations.filter { it.isAvailable() }.forEach { it.noteOn(pitch, velocity, channel) }

        if (durationMs > 0) {
            pendingOffPitch = pitch
            val runnable = Runnable {
                if (pendingOffPitch == pitch) {
                    doNoteOff(pitch, channel)
                    pendingOffPitch = -1
                }
            }
            pendingOff = runnable
            handler.postDelayed(runnable, durationMs)
        }
    }

    fun noteOff(pitch: Int, channel: Int = 1) = runOnNoteThread { doNoteOff(pitch, channel) }

    private fun doNoteOff(pitch: Int, channel: Int) {
        destinations.filter { it.isAvailable() }.forEach { it.noteOff(pitch, channel) }
        if (pitch == lastPitch) lastPitch = -1
    }

    fun allNotesOff(channel: Int = 1) = runOnNoteThread {
        pendingOff?.let { handler.removeCallbacks(it) }
        pendingOff = null
        pendingOffPitch = -1
        // Release on the channel the note was actually sent on — the caller's
        // channel may already reflect a new selection.
        if (lastPitch >= 0) {
            doNoteOff(lastPitch, lastChannel)
            lastPitch = -1
        }
    }

    fun release() {
        allNotesOff()
        noteThread.quitSafely()
    }
}

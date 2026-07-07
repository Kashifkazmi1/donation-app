package com.givewp.donationterminal

import android.app.Application
import com.stripe.stripeterminal.TerminalApplicationDelegate
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DonationTerminalApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Required by the Stripe Terminal SDK: observes the process lifecycle so the SDK can
        // manage its background/foreground behavior (e.g. Bluetooth reconnection). The actual
        // `Terminal.init(...)` call happens lazily in TerminalManager the first time a
        // reader-related repository method is invoked, once Hilt-provided dependencies (the
        // connection token provider) exist.
        TerminalApplicationDelegate.onCreate(this)
    }
}

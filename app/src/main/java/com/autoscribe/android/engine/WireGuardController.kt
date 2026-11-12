package com.autoscribe.android.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Controls the official WireGuard app through its documented remote-control
 * broadcasts (the same mechanism Tasker uses):
 *
 *   action  com.wireguard.android.action.SET_TUNNEL_UP / SET_TUNNEL_DOWN
 *   package com.wireguard.android, extra "tunnel" = tunnel name, broadcast
 *
 * Requirements, all checked by the UI before a run starts:
 *  1. The WireGuard app is installed.
 *  2. "Allow remote control apps" is on in the WireGuard app's Settings.
 *  3. This app holds com.wireguard.android.permission.CONTROL_TUNNELS
 *     (requested at runtime; the WireGuard app prompts the user once).
 *  4. The user has toggled a tunnel by hand at least once, accepting the
 *     system VPN permission prompt (VpnService.prepare).
 *
 * Connection detection: the WireGuard app publishes no external state
 * broadcast, so "connected" means Android reports an active VPN transport
 * (ConnectivityManager / TRANSPORT_VPN), exactly as any third-party app
 * must observe it. A tunnel whose name does not exist simply never brings
 * the VPN up, so the connect wait times out and the test records FAILURE —
 * the Tunnels tab's Probe action exists to catch naming mistakes early.
 */
class WireGuardController(private val context: Context) {

    fun isInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    PACKAGE, PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(PACKAGE, 0)
            }
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    fun hasControlPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, CONTROL_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    fun launchAppIntent(): Intent? =
        context.packageManager.getLaunchIntentForPackage(PACKAGE)

    fun tunnelUp(name: String) {
        context.sendBroadcast(
            Intent(ACTION_UP).setPackage(PACKAGE).putExtra(EXTRA_TUNNEL, name),
        )
    }

    fun tunnelDown(name: String) {
        context.sendBroadcast(
            Intent(ACTION_DOWN).setPackage(PACKAGE).putExtra(EXTRA_TUNNEL, name),
        )
    }

    /** True when Android reports an active VPN network. Never throws. */
    fun vpnActive(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val PACKAGE = "com.wireguard.android"
        const val ACTION_UP = "com.wireguard.android.action.SET_TUNNEL_UP"
        const val ACTION_DOWN = "com.wireguard.android.action.SET_TUNNEL_DOWN"
        const val EXTRA_TUNNEL = "tunnel"
        const val CONTROL_PERMISSION = "com.wireguard.android.permission.CONTROL_TUNNELS"
    }
}

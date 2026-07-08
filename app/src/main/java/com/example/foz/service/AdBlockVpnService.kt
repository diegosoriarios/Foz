package com.example.foz.service

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log

class AdBlockVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        try {
            val builder = Builder()
                .setSession("Foz AdBlocker")
                .addAddress("10.0.0.2", 32)
                .addDnsServer("94.140.14.14")
                .addDnsServer("94.140.15.15")
                // We don't add routes, so no actual IP traffic will be routed through the TUN interface
                // EXCEPT for DNS if the system decides to use these DNS servers for the VPN.
                // By adding a dummy address and setting DNS, we trick the system into using our DNS.

            vpnInterface = builder.establish()
            Log.d("AdBlockVpnService", "VPN established with AdGuard DNS")
        } catch (e: Exception) {
            Log.e("AdBlockVpnService", "Failed to establish VPN", e)
            stopSelf()
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e("AdBlockVpnService", "Error closing VPN interface", e)
        }
        vpnInterface = null
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.foz.service.STOP_VPN"
    }
}

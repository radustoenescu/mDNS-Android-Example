package org.pdsd.pingpong.network;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * Author: Radu Stoenescu
 * Don't be a stranger,  osmosis.7.radustoe@spamgourmet.com
 */
public class AndroidWiFiTCPServer extends TCPServer {
    private static final String LOG_TAG = "Android WiFi TCP Server";

    public AndroidWiFiTCPServer(InetAddress address) throws IOException {
        super(address);
    }

    public static AndroidWiFiTCPServer build(Context androidContext) {
        /**
         * We need to know our identity inside the local WiFi network.
         */
        WifiManager wifi = (android.net.wifi.WifiManager)
                androidContext.getSystemService(android.content.Context.WIFI_SERVICE);
        InetAddress deviceIpAddress;

        try {
//                Get the IP the server will be bound to.
            deviceIpAddress = InetAddress.getByAddress(
                    ByteBuffer.allocate(4).putInt(
                            Integer.reverseBytes(wifi.getConnectionInfo().getIpAddress())).array());

            if (deviceIpAddress == null)
                throw new IOException("No IP address can be found");
            Log.i(LOG_TAG, "My address is " + deviceIpAddress.getHostAddress());
//                Start the server
            return new AndroidWiFiTCPServer(deviceIpAddress);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error starting serviceServer " + e.getMessage());
            return null;
        }
    }
}

package org.pdsd.pingpong.service;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.provider.Settings;
import android.util.Log;
import org.pdsd.pingpong.network.PingClient;
import org.pdsd.pingpong.network.PongServer;

import javax.jmdns.*;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * Author: Radu Stoenescu
 * Don't be a stranger,  osmosis.7.radustoe@spamgourmet.com
 */
public class PingPongService {

    private static String TAG = "JmDNS App";
    /**
     * If no identity is provided, a generic one is used.
     */
    private static String DEFAULT_ID_PREFIX = "annon-";
    /**
     * What type of service should be advertised.
     */
    private static String SERVICE_TYPE = "_pingpong._tcp.local.";
    /**
     * What request should be sent to the service running on other peers ?
     */
    private static String MESSAGE = "Ping ";

    private PongServer service;
    private String devId;
    /**
     * Required for multicast communication.
     */
    private WifiManager.MulticastLock lock;
    /**
     * Service discovery and advertisement.
     */
    private JmDNS jmdns;
    private ServiceInfo serviceInfo;
    private Context ctx;

    public PingPongService(String nodeId, Context androidContext) {
        ctx = androidContext;
        String prefix;
        if (nodeId != null) {
            prefix = nodeId;
        } else {
            prefix = DEFAULT_ID_PREFIX;
        }
        devId = prefix + Settings.Secure.getString(androidContext.getContentResolver(),
                Settings.Secure.ANDROID_ID).substring(0, 5);

        /**
         * We need to know our identity inside the local WiFi network.
         */
        WifiManager wifi = (android.net.wifi.WifiManager)
                androidContext.getSystemService(android.content.Context.WIFI_SERVICE);
        InetAddress deviceIpAddress = null;

        try {
//                Get the IP the server will be bound to.
            deviceIpAddress = InetAddress.getByAddress(
                    ByteBuffer.allocate(4).putInt(
                            Integer.reverseBytes(wifi.getConnectionInfo().getIpAddress())).array());

            if (deviceIpAddress == null)
                throw new IOException("No IP address can be found");
            Log.i(TAG, "My address is " + deviceIpAddress.getHostAddress());
//                Start the server
            service = new PongServer(deviceIpAddress);
        } catch (IOException e) {
            Log.e(TAG, "Error starting service " + e.getMessage());
        }

//            Aquire lock for multicast communication.
        lock = wifi.createMulticastLock(getClass().getName());
        lock.setReferenceCounted(true);
        lock.acquire();

        Log.d(TAG, "Starting jmDNS service");
        try {
            jmdns = JmDNS.create(deviceIpAddress, deviceIpAddress.getHostName());
//                Define the behavior of service discovery.
            jmdns.addServiceTypeListener(new PingPongServiceTypeListener());

//                Advertise the local service in the network
            serviceInfo = ServiceInfo.create(SERVICE_TYPE, devId, service.listenPort(), "ping pong");
            jmdns.registerService(serviceInfo);
        } catch (IOException e) {
            Log.e(TAG, "Error starting jmDNS instance" + e.getMessage());
        }
    }

    public boolean stop() {
        service.kill();

        if (jmdns != null) {
            jmdns.unregisterAllServices();
            try {
                jmdns.close();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                return false;
            }
            Log.i(TAG, "Services unregistered");
            jmdns = null;
        }

        if (lock != null) {
            Log.i(TAG, "Releasing multicast lock");
            lock.release();
            lock = null;
        }

        return true;
    }

    public boolean changeId(String prefix) {
        String newId = prefix + Settings.Secure.getString(ctx.getContentResolver(),Settings.Secure.ANDROID_ID).
                substring(0, 5);
        if (newId.equals(devId)) {
            return true;
        } else {
            jmdns.unregisterService(serviceInfo);
            devId = newId;
            serviceInfo = ServiceInfo.create(SERVICE_TYPE, devId, service.listenPort(), "ping pong");
            try {
                jmdns.registerService(serviceInfo);
                Log.d(TAG, "Identity changed and advertised");
                return true;
            } catch (IOException e) {
                Log.e(TAG, "Cannot change identity: " + e.getMessage());
                return false;
            }
        }
    }

    private class PingPongServiceTypeListener implements ServiceTypeListener {

        @Override
        public void serviceTypeAdded(ServiceEvent event) {
            // A new service provider was discovered, is it running the service I want ?
            if (event.getType().equals(SERVICE_TYPE)) {
                Log.d("TAG", "Same service discovered");

                /**
                 * I am interested in receiving events about this service type.
                 */
                jmdns.addServiceListener(event.getType(), new ServiceListener() {
                    @Override
                    public void serviceAdded(ServiceEvent serviceEvent) {
                        Log.i(TAG, "Service added " + serviceEvent.getInfo().toString());
                    }

                    @Override
                    public void serviceRemoved(ServiceEvent serviceEvent) {
                        Log.i(TAG, "Service removed " + serviceEvent.getInfo().toString());
                    }

                    @Override
                    public void serviceResolved(final ServiceEvent serviceEvent) {
                        Log.i(TAG, "Peer found " + serviceEvent.getInfo().toString());

//                                    If I'm not the newly discovered peer, engage in communication
                        if (!serviceEvent.getName().equals(devId)) {
//                                        Send request to other peer
                            new AsyncTask<String, String, String>() {
                                @Override
                                protected String doInBackground(String... strings) {
                                    try {
                                        for (InetAddress i : serviceEvent.getInfo().getInet4Addresses()) {
                                            Log.d(TAG, "Other peer is: " + i.getHostAddress());
                                        }
                                        Log.i(TAG, "Requesting " + strings[0]);
                                        return PingClient.sendTo(strings[0],
                                                serviceEvent.getInfo().getInetAddresses()[0],
                                                serviceEvent.getInfo().getPort());
                                    } catch (IOException e) {
                                        Log.e(TAG, "Error in request:" + e.getMessage());
                                        return null;
                                    }
                                }

                                @Override
                                protected void onPostExecute(String s) {
                                    Log.d(TAG, s);
                                }
                            }.execute(MESSAGE);
                        } else {
                            Log.d(TAG, "I found myself");
                        }
                    }});

//                            Request information about the service.
                jmdns.requestServiceInfo(event.getType(), event.getName());
            }

            Log.i(TAG, "Service discovered: " + event.getType() + " : " + event.getName());
        }

        @Override
        public void subTypeForServiceTypeAdded(ServiceEvent ev) {}
    }
}

package org.pdsd.pingpong.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import org.pdsd.pingpong.network.TCPClient;
import org.pdsd.pingpong.network.TCPServer;

import javax.jmdns.*;
import javax.jmdns.impl.ServiceInfoImpl;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownServiceException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Author: Radu Stoenescu
 * Don't be a stranger,  osmosis.7.radustoe@spamgourmet.com
 */
public class NetworkService {

    /**
     * The hooks allow a gentle mechanism of stepping into the normal
     * process of setup and teardown of a JmDNS service for
     * platform-specific actions (such as acquiring and releasing a
     * WiFi multicast lock for Android)
     */
    public interface ServiceSetupHook {
        /**
         * Called before creating the JmDNS service
         */
        boolean setup();
    }

    public interface ServiceTeardownHook {
        /**
         * Called after the JmDNS service was stopped
         */
        boolean teardown();
    }

    public interface ServiceEventHandler {
        void handle(ServiceInfo si);
    }

    private static final String LOG_TAG = "JmDNS Service";

    /**
     * If no identity is provided, a generic one is used.
     */
    private static final String DEFAULT_HOST_ID_PREFIX = "annon-";

    /**
     * What type of serviceServer should be advertised.
     */
    private static String SERVICE_TYPE = "_pingpong._tcp.local.";
    /**
     * What request should be sent to the serviceServer running on other peers ?
     */
    private static String REQUEST_MESSAGE = "Ping ";

    private TCPServer serviceServer;
    private String devId;
    private ServiceTeardownHook teardownHook;

    /**
     * Service discovery and advertisement.
     */
    private JmDNS jmdns;
    private ServiceInfo serviceInfo;

    private Set<ServiceInfo> discoveredPeers = new TreeSet<ServiceInfo>(new Comparator<ServiceInfo>() {
        @Override
        public int compare(ServiceInfo si1, ServiceInfo si2) {
            return si1.getName().compareTo(si2.getName());
        }
    });
    private ServiceEventHandler onNew, onRemove;

    public void setOnNewServiceCallback(ServiceEventHandler callback) {
        onNew = callback;
    }

    public void setOnServiceRemovedCallback(ServiceEventHandler callback) {
        onRemove = callback;
    }

    public List<ServiceInfo> getPeers() {
        List<ServiceInfo> peers = new LinkedList<ServiceInfo>();
        peers.addAll(discoveredPeers);
        return peers;
    }

    public NetworkService(String nodeId, TCPServer serviceServer,
                          ServiceSetupHook setupStub, ServiceTeardownHook teardownStub) throws UnknownServiceException {
        teardownHook = teardownStub;
        this.serviceServer = serviceServer;

//        Add a random suffix to make the device id unique
        String prefix;
        if (nodeId != null) {
            prefix = nodeId;
        } else {
            prefix = DEFAULT_HOST_ID_PREFIX;
        }
        devId = prefix + new Random().nextInt();

//        Call the setup stub
        if (! setupStub.setup()) {
            throw new UnknownServiceException("Error during NSD setup");
        }

        Log.d(LOG_TAG, "Starting jmDNS serviceServer");
        try {
            jmdns = JmDNS.create(serviceServer.listenAddress(), serviceServer.listenAddress().getHostName());
//                Define the behavior of serviceServer discovery.
            jmdns.addServiceTypeListener(new PingPongServiceTypeListener());

//                Advertise the local serviceServer in the network
            serviceInfo = ServiceInfo.create(SERVICE_TYPE, devId, serviceServer.listenPort(), "ping pong");
            jmdns.registerService(serviceInfo);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Error starting jmDNS instance" + e.getMessage());
        }
    }

    public boolean stop() {
        serviceServer.kill();

        if (jmdns != null) {
            jmdns.unregisterAllServices();
            try {
                jmdns.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                return false;
            }
            Log.i(LOG_TAG, "Services unregistered");
            jmdns = null;
        }

        teardownHook.teardown();

        return true;
    }

    public boolean changeId(String prefix) {
        String newId = prefix + new Random().nextInt();
        if (newId.equals(devId)) {
            return true;
        } else {
            jmdns.unregisterService(serviceInfo);
            devId = newId;
            serviceInfo = ServiceInfo.create(SERVICE_TYPE, devId, serviceServer.listenPort(), "ping pong");
            try {
                jmdns.registerService(serviceInfo);
                Log.d(LOG_TAG, "Identity changed and advertised");
                return true;
            } catch (IOException e) {
                Log.e(LOG_TAG, "Cannot change identity: " + e.getMessage());
                return false;
            }
        }
    }

    private class PingPongServiceTypeListener implements ServiceTypeListener {
        @Override
        public void serviceTypeAdded(ServiceEvent event) {
            // A new serviceServer provider was discovered, is it running the serviceServer I want ?
            if (event.getType().equals(SERVICE_TYPE)) {
                Log.d("LOG_TAG", "Same serviceServer discovered");

                /**
                 * I am interested in receiving events about this serviceServer type.
                 */
                jmdns.addServiceListener(event.getType(), new ServiceListener() {
                    @Override
                    public void serviceAdded(ServiceEvent serviceEvent) {
                        Log.i(LOG_TAG, "Service added " + serviceEvent.getInfo().toString());
                    }

                    @Override
                    public void serviceRemoved(ServiceEvent serviceEvent) {
                        Log.i(LOG_TAG, "Service removed " + serviceEvent.getInfo().toString());
                        discoveredPeers.remove(serviceEvent.getInfo());
                        onRemove.handle(serviceEvent.getInfo());
                    }

                    @Override
                    public void serviceResolved(final ServiceEvent serviceEvent) {
                        Log.i(LOG_TAG, "Peer found " + serviceEvent.getInfo().toString());

//                                    If I'm not the newly discovered peer, engage in communication
                        if (!serviceEvent.getName().equals(devId)) {
//                                        Send request to other peer
                            new AsyncTask<String, String, ServiceInfo>() {
                                @Override
                                protected ServiceInfo doInBackground(String... strings) {
                                    try {
                                        for (InetAddress i : serviceEvent.getInfo().getInet4Addresses()) {
                                            Log.d(LOG_TAG, "Other peer is: " + i.getHostAddress());
                                        }
                                        Log.i(LOG_TAG, "Requesting " + strings[0]);
                                        final String response = TCPClient.sendTo(strings[0],
                                                serviceEvent.getInfo().getInetAddresses()[0],
                                                serviceEvent.getInfo().getPort());
                                        Log.d(LOG_TAG, response);
                                        return serviceEvent.getInfo();
                                    } catch (IOException e) {
                                        Log.e(LOG_TAG, "Error in request:" + e.getMessage());
                                        return null;
                                    }
                                }

                                @Override
                                protected void onPostExecute(ServiceInfo s) {
                                    discoveredPeers.add(s);
                                    onNew.handle(s);
                                }
                            }.execute(REQUEST_MESSAGE);
                        } else {
                            Log.d(LOG_TAG, "I found myself");
                        }
                    }});

//                            Request information about the serviceServer.
                jmdns.requestServiceInfo(event.getType(), event.getName());
            }

            Log.i(LOG_TAG, "Service discovered: " + event.getType() + " : " + event.getName());
        }

        @Override
        public void subTypeForServiceTypeAdded(ServiceEvent ev) {}
    }
}

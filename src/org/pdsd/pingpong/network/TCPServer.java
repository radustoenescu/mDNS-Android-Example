package org.pdsd.pingpong.network;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Author: Radu Stoenescu
 * Don't be a stranger to pingpong.7.radustoe@spamgourmet.com
 *
 * Code for a simple TCP "echo"-like server.
 */
public class TCPServer {
    private static final String LOG_TAG = "Server";
    private static final String RESPONSE = "Pong";

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private volatile boolean alive;

    /**
     * @return local port on which the server runs.
     */
    public int listenPort() {
        if (serverSocket != null)
            return  serverSocket.getLocalPort();
        else
            throw new IllegalStateException("ServerSocket is null, probably not closed or not initialized");
    }

    /**
     * @return INetAddr of the server.
     */
    public InetAddress listenAddress() {
        if (serverSocket != null)
            return  serverSocket.getInetAddress();
        else
            throw new IllegalStateException("ServerSocket is null, probably not closed or not initialized");
    }

    /**
     * Constructor that starts the server and binds it to the provided address.
     * @param bindAddress Server IP address.
     * @throws IOException
     */
    public TCPServer(final InetAddress bindAddress) throws IOException {
//        We are not using a predefined port, it will be provided by the system and then advertised to other peers
        serverSocket = new ServerSocket(0, 10, bindAddress);
        alive = true;
//        Requests will be served from a standalone thread
        new Thread(new Runnable() {
            public void run() {
                try {
//                    As long as the serve was not killed, loop and accept requests
                    while (alive) {
                        // Wait for a connection
                        clientSocket = serverSocket.accept();
                        Log.d(LOG_TAG, "Request received from: " + clientSocket.getRemoteSocketAddress().toString());
                        //Service the connection
                        serve(clientSocket);
                    }
//                    When killed, release resources.
                    serverSocket.close();
                } catch (IOException ioe) {
                    Log.e(LOG_TAG, "Error in TCPServer: " + ioe.getMessage());
                }
            }
        }).start();
    }

    /**
     * Reads a string (UTF) request and responds to it.
     * @param client socket the request is originated
     * @throws IOException
     */
    public void serve(Socket client) throws IOException {
        DataInputStream inbound;
        DataOutputStream outbound;
        String request = "";
        String response;
        try {
            // Acquire the streams for IO
            inbound = new DataInputStream(client.getInputStream());
            outbound = new DataOutputStream(client.getOutputStream());

            request = inbound.readUTF();
            Log.d(LOG_TAG, "Incoming request " + request);
            client.shutdownInput();

            response = buildResponse(request);
            outbound.writeUTF(response);
            client.shutdownOutput();
        } finally {
            client.close();
            Log.d(LOG_TAG, "Sent response " + String.valueOf(request));
        }
    }

    /**
     * This method should be overwritten by more complicated server implementations.
     *
     * It dictates what string response is sent back to a client, based on client's request.
     * @param request Client's request
     * @return String representation of the response.
     */
    protected String buildResponse(String request) {
        return request + " " + RESPONSE;
    }

    /**
     * Gracefully signals the server to halt the loop.
     */
    public void kill() {
        alive = false;
    }
}

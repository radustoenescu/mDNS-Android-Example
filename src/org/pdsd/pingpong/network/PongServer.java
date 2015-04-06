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
 * Code for a simple TCP "echo"-like serve.
 */
public class PongServer {
    private static String TAG = "Server";
    private static String RESPONSE = "Pong";

    private ServerSocket serverSocket;
    private Socket clientSocket;
    private boolean alive;

    /**
     * On what port does to serve run ?
     * @return serve TCP port
     */
    public int listenPort() {
        if (serverSocket != null)
            return  serverSocket.getLocalPort();
        else
            throw new IllegalStateException("ServerSocket is null, probably not closed or not initialized");
    }

    /**
     * Constructor that starts the serve and binds it to the provided address.
     * @param bindAddress Server IP address.
     * @throws IOException
     */
    public PongServer(final InetAddress bindAddress) throws IOException {
//        We are not using a predefined port, it will be provided by the system and then advertised to other peers
        serverSocket = new ServerSocket(0, 10, bindAddress);
        alive = true;
//        Requests will be served from a standlaone thread
        new Thread(new Runnable() {
            public void run() {
                try {
//                    As long as the serve was not killed, loop and accept requests
                    while (alive) {
                        // Wait for a connection
                        clientSocket = serverSocket.accept();
                        Log.d(TAG, "Request received from: " + clientSocket.getRemoteSocketAddress().toString());
                        //Service the connection
                        serve(clientSocket);
                    }
//                    When killed, release resources.
                    serverSocket.close();
                } catch (IOException ioe) {
                    Log.e(TAG, "Error in PongServer: " + ioe.getMessage());
                }
            }
        }).start();
    }

    /**
     * Reads a string (UTF) request and responds to it.
     * @param client socket the request is originated
     * @throws IOException
     */
    public static void serve(Socket client) throws IOException {
        DataInputStream inbound;
        DataOutputStream outbound;
        String message = "Pong";
        try {
            // Acquire the streams for IO
            inbound = new DataInputStream(client.getInputStream());
            outbound = new DataOutputStream(client.getOutputStream());

            message = inbound.readUTF();
            Log.d(TAG, "Incoming request " + message);
            client.shutdownInput();
//            Appending server response
            message += RESPONSE;
            outbound.writeUTF(message);
            client.shutdownOutput();
        } finally {
            client.close();
            Log.d(TAG, "Sent response " + String.valueOf(message));
        }
    }

    /**
     * Signals the server to halt the loop.
     */
    public void kill() {
        alive = false;
    }
}

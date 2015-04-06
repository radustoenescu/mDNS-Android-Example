package org.pdsd.pingpong.network;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Author: Radu Stoenescu
 * Don't be a stranger pingpong.7.radustoe@spamgourmet.com
 *
 * Code for sending a string request to a serve via a TCP client socket and
 * receiving its response.
 */
public class PingClient {
    /**
     * Sends a string via a TCP socket (in UTF format), waits for a response and returns it (also UTF string).
     * @param str Request
     * @param destination IP address
     * @param port Port
     * @return Response from the other host.
     * @throws IOException
     */
    public static String sendTo(String str, InetAddress destination, int port) throws IOException {
        Socket socket = null;
        DataOutputStream writer;
        DataInputStream reader;

        try {
            socket = new Socket(destination, port);
            writer = new DataOutputStream(socket.getOutputStream());
            writer.writeUTF(str);
//            Close the output stream to signal there is no more data to be send.
            socket.shutdownOutput();
//            Read response.
            reader = new DataInputStream(socket.getInputStream());
            return reader.readUTF();
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    /**
     * Convenience wrapper method.
     * @param str
     * @param host
     * @param port
     * @return
     * @throws IOException
     */
    static String sendTo(String str, String host, int port) throws IOException {
        return sendTo(str, InetAddress.getByName(host), port);
    }
}

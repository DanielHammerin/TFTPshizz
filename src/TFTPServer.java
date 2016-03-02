
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;

public class TFTPServer {
    public static final int TFTPPORT = 4950;
    public static final int BUFSIZE = 512;
    public static final String READDIR = "src/read/";
    public static final String WRITEDIR = "src/write/";
    public static final int OP_RRQ = 1;                                 //Op code for read request
    public static final int OP_WRQ = 2;                                 //Op code for write request
    public static final int OP_DAT = 3;                                 //Op code for data
    public static final int OP_ACK = 4;                                 //Op code for acknowledge
    public static final int OP_ERR = 5;                                 //Op code for error

    public static void main(String[] args) {
        if (args.length > 0) {
            System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
            System.exit(1);
        }
        try {
            TFTPServer server= new TFTPServer();
            server.start();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }
    private void start() throws SocketException {
        byte[] buf= new byte[BUFSIZE];

		/* Create socket */
        DatagramSocket socket= new DatagramSocket(null);

		/* Create local bind point */
        SocketAddress localBindPoint= new InetSocketAddress(TFTPPORT);
        socket.bind(localBindPoint);

        System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

        while(true) {        /* Loop to handle various requests */
            final InetSocketAddress clientAddress = receiveFrom(socket, buf);

            if (clientAddress == null) /* If clientAddress is null, an error occurred in receiveFrom()*/
                continue;

            final StringBuffer requestedFile = new StringBuffer();
            final int reqtype = ParseRQ(buf, requestedFile);

            new Thread() {
                public void run() {
                    try {
                        DatagramSocket sendSocket= new DatagramSocket(0);

                        System.out.printf("%s request for %s from %s using port %d\n",
                                (reqtype == OP_RRQ)?"Read":"Write",
                                clientAddress.getHostName(), clientAddress.getPort());

                        if (reqtype == OP_RRQ) {      /* read request */
                            requestedFile.insert(0, READDIR);
                            HandleRQ(sendSocket, requestedFile.toString(), OP_RRQ);
                        }
                        else {                       /* write request */
                            requestedFile.insert(0, WRITEDIR);
                            HandleRQ(sendSocket,requestedFile.toString(),OP_WRQ);
                        }
                        sendSocket.close();
                    } catch (SocketException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }
    /**
     * Reads the first block of data, i.e., the request for action (read or write).
     * @param socket socket to read from
     * @param buf where to store the read data
     * @return the Internet socket address of the client
     */

    private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
        InetSocketAddress addr = new InetSocketAddress(socket.getInetAddress(), socket.getPort());
        DatagramPacket recievepacket = new DatagramPacket(buf, buf.length);
        try {
            socket.receive(recievepacket);
        } catch (IOException e) {
            System.out.println("Shitsticks happened yao.");
            e.printStackTrace();
        }
        return addr;
    }

    private int ParseRQ(byte[] buf, StringBuffer requestedFile) {
        ByteBuffer wrap = ByteBuffer.wrap(buf);
        short opcode = wrap.getShort();
        String fileName = new String(buf, 2, buf.length-2);
        requestedFile.append(fileName);
        return opcode;
    }

    private void HandleRQ(DatagramSocket sendSocket, String string, int opRrq) {

    }
}





import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;

public class TFTPServer {
    public static final int TFTPPORT = 4950;
    public static final int BUFSIZE = 516;
    public static final String READDIR = "src/read/w0ah.txt";
    public static final String WRITEDIR = "src/write/awshiet.txt";
    public static final int OP_RRQ = 1;                                 //Op code for read request
    public static final int OP_WRQ = 2;                                 //Op code for write request
    public static final int OP_DAT = 3;                                 //Op code for data
    public static final int OP_ACK = 4;                                 //Op code for acknowledge
    public static final int OP_ERR = 5;                                 //Op code for error
    public static byte[] buf;
    public static FileOutputStream fos;
    public static String mode;

    public static void main(String[] args) {
        if (args.length > 0) {
            System.err.printf("usage: java %s\n", TFTPServer.class.getCanonicalName());
            System.exit(1);
        }
        try {
            TFTPServer server = new TFTPServer();
            server.start();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private void start() throws SocketException {
        buf = new byte[BUFSIZE];

		/* Create socket */
        DatagramSocket socket = new DatagramSocket(null);

		/* Create local bind point */
        SocketAddress localBindPoint = new InetSocketAddress(TFTPPORT);
        socket.bind(localBindPoint);

        System.out.printf("Listening at port %d for new requests\n", TFTPPORT);

        while (true) {        /* Loop to handle various requests */
            final InetSocketAddress clientAddress = receiveFrom(socket, buf);

            if (clientAddress == null) /* If clientAddress is null, an error occurred in receiveFrom()*/
                continue;

            final StringBuffer requestedFile = new StringBuffer();
            final int reqtype = ParseRQ(buf, requestedFile);

            new Thread() {
                public void run() {
                    try {
                        DatagramSocket clientSocket = new DatagramSocket(0);
                        clientSocket.connect(clientAddress);

                        System.out.printf("%s request for %s from %s using port %d\n",
                                (reqtype == OP_RRQ) ? "Read" : "Write",
                                clientAddress.getHostName(), clientAddress.getPort());

                        if (reqtype == OP_RRQ) {      /* read request */
                            requestedFile.insert(0, READDIR);
                            HandleRQ(clientSocket, requestedFile.toString(), OP_RRQ);
                        } else {                       /* write request */
                            requestedFile.insert(0, WRITEDIR);
                            HandleRQ(clientSocket, requestedFile.toString(), OP_WRQ);
                        }
                        clientSocket.close();
                    } catch (SocketException e) {
                        e.printStackTrace();
                    }
                }
            }.start();
        }
    }

    /**
     * Reads the first block of data, i.e., the request for action (read or write).
     *
     * @param socket socket to read from
     * @param buf    where to store the read data
     * @return the Internet socket address of the client
     */

    private InetSocketAddress receiveFrom(DatagramSocket socket, byte[] buf) {
        DatagramPacket pack = new DatagramPacket(buf, buf.length);
        try {
            socket.receive(pack);
        } catch (IOException e) {
            e.printStackTrace();
        }
        InetSocketAddress addr = new InetSocketAddress(pack.getAddress(), pack.getPort());
        return addr;
    }

    private int ParseRQ(byte[] buf, StringBuffer requestedFile) {
        ByteBuffer wrap = ByteBuffer.wrap(buf);
        short opcode = wrap.getShort();
        int n = -1;
        for (int i = 2; i < buf.length; i++) {
            if (buf[i] == 0) {
                n = i;
                break;
            }
        }
        if (n == -1) {
            System.out.println("Request packet corrupted. Connection terminated");
            System.exit(1);
        }

        String fileName = new String(buf, 2, buf.length - 2);
        requestedFile.append(fileName);

        for (int i = n+1; i < buf.length; i++) {
            if (buf[i] == 0) {
                String s = new String(buf, n+1, i-(n-1));
                mode = s;
                if (s.equalsIgnoreCase("octet")) {
                    return opcode;
                }
                else {
                    System.out.println("I fucked your mother last nigt <3");
                    System.exit(1);
                }
            }
        }
        return 0;
    }

    private void HandleRQ(DatagramSocket clientSocket, String requestedFilestring, int opRrq) {
        DatagramPacket datapacket = new DatagramPacket(buf, buf.length);
        switch (opRrq) {
            case 1:     //Write
                try {
                    File file = new File(requestedFilestring);
                    fos = new FileOutputStream(file);
                    datapacket.setData();
                    clientSocket.send();
                }
                catch () {

                }
            case 2:     //Read
                while (true) {
                    try {
                        clientSocket.receive(recieveData);
                        if (recieveData.getLength() == 512) {
                            fos.write(recieveData.getData());
                        }
                        else {
                            fos.write(recieveData.getData(), recieveData.getOffset(), recieveData.getLength());
                            fos.close();
                            clientSocket.close();
                            break;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

        }
    }
}




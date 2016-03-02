
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;

public class TFTPServer {
    public static final int TFTPPORT = 4950;
    public static final int BUFSIZE = 516;
    public static final String READDIR = "src/read/w0ah.txt";
    public static final String WRITEDIR = "src/write/awshiet.txt";
    public static final short OP_RRQ = 1;                                 //Op code for read request
    public static final short OP_WRQ = 2;                                 //Op code for write request
    public static final short OP_DAT = 3;                                 //Op code for data
    public static final short OP_ACK = 4;                                 //Op code for acknowledge
    public static final short OP_ERR = 5;                                 //Op code for error
    public static final short ERR_LOST = 0;             //When a file gets lost
    public static final short ERR_FNF = 1;              //when a file is not found
    public static final short ERR_ACCESS = 2;           
    public static final short ERR_EXISTS = 6;
    public static byte[] buf;
    public static FileOutputStream fos;
    public static String mode;
    public static final String[] errorCodes = {"Not defined", "File not found.", "Access violation.",
            "Disk full or allocation exceeded.", "Illegal TFTP operation.",
            "Unknown transfer ID.", "File already exists.",
            "No such user."};

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
        int n = -1; //delimiter
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

        for (int i = n + 1; i < buf.length; i++) {
            if (buf[i] == 0) {
                String s = new String(buf, n + 1, i - (n - 1));
                mode = s;
                if (s.equalsIgnoreCase("octet")) {
                    return opcode;
                } else {
                    System.out.println("I fucked your mother last nigt <3");
                    System.exit(1);
                }
            }
        }
        return 0;
    }

    private void HandleRQ(DatagramSocket clientSocket, String requestedFilestring, int opRrq) {
        DatagramPacket datapacket = new DatagramPacket(buf, buf.length);

        System.out.println(requestedFilestring);
        File file = new File(requestedFilestring);
        byte[] buf = new byte[BUFSIZE - 4];
        switch (opRrq) {
            case 1:     //Write
                FileInputStream in = null;
                try {
                    in = new FileInputStream(file);

                } catch (FileNotFoundException e) {
                    System.err.println("file not found ");
                    sendError(clientSocket, ERR_FNF, "");
                    return;
                }

                short blockNum = 1;

                while (true) {
                    int length;
                    try {
                        length = in.read(buf);
                    } catch (IOException e) {
                        System.err.println("Cannot read file");
                        return;
                    }
                    if (length == -1) {
                        length = 0;
                    }
                    DatagramPacket sender = dataPacket(blockNum, buf, length);
                    System.out.println("sending file....");
                    if (WriteAndReadAck(clientSocket, sender, blockNum++)) {
                        System.out.println("file is sending " + blockNum);

                    } else {
                        System.err.println("Error. Lost connection.");
                        sendError(clientSocket, ERR_LOST, "Lost Connection.");
                        return;
                    }

                    if (length < 512) {
                        try {
                            in.close();
                        } catch (IOException e) {
                            System.err.println("cannot close the file");
                        }
                        break;
                    }
                }
            case 2:     //Read

                if (file.exists()) {
                    System.out.println("file is already there");
                    sendError(clientSocket, ERR_EXISTS, "File already exists" );
                    return;
                } else {
                    FileOutputStream out = null;
                    try {
                        out = new FileOutputStream(file);

                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        sendError(clientSocket, ERR_ACCESS, "Could not Create File");
                        return;
                    }


                    blockNum = 0;


                    while (true) {
                        DatagramPacket datagramPacket = ReadAndWriteData(clientSocket, ackPacket(blockNum++), blockNum);

                        if (datagramPacket == null) {
                            System.err.println("Lost the connection");
                            sendError(clientSocket, ERR_LOST, "cant find the connection");
                            try {
                                out.close();
                            } catch (IOException e) {
                                System.err.println("could not close the file");
                                file.delete();
                                break;
                            }
                        } else {
                            byte[] data = datagramPacket.getData();
                            try {
                                out.write(data, 4, datagramPacket.getLength() - 4);
                                System.out.println(datagramPacket.getLength());
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.out.println("cannto write the data");
                                sendError(clientSocket, ERR_ACCESS, "cannot write data");

                            }
                        }
                        if (datagramPacket.getLength() - 4 < 512) {
                            try {
                                clientSocket.send(ackPacket(blockNum));
                            } catch (IOException e) {
                                try {
                                    clientSocket.send(ackPacket(blockNum));
                                } catch (IOException e1) {

                                }
                                System.out.println("File has been written");

                                try {
                                    out.close();
                                } catch (IOException e1) {
                                    System.err.println("could not close the file");
                                }
                                break;
                            }
                        }


                    }
                }

        }
    }

    private DatagramPacket dataPacket(short block, byte[] data, int length) {

        ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
        buffer.putShort((short) OP_DAT);
        buffer.putShort(block);
        buffer.put(data, 0, length);

        return new DatagramPacket(buffer.array(), 4 + length);
    } // dataPacket


    private DatagramPacket ReadAndWriteData(DatagramSocket sendSocket, DatagramPacket sendAck, short block) {
        int retryCount = 0;
        byte[] rec = new byte[BUFSIZE];
        DatagramPacket receiver = new DatagramPacket(rec, rec.length);

        while (true) {
            if (retryCount >= 6) {
                System.err.println("Timed out. Closing connection.");
                return null;
            }
            try {
                System.out.println("sending ack for block: " + block);
                sendSocket.send(sendAck);
                sendSocket.setSoTimeout(((int) Math.pow(2, retryCount++)) * 1000);
                sendSocket.receive(receiver);

                short blockNum = getData(receiver);
                System.out.println(blockNum + " " + block);
                if (blockNum == block) {
                    return receiver;
                } else if (blockNum == -1) {
                    return null;
                } else {
                    System.out.println("Duplicate.");
                    retryCount = 0;
                    throw new SocketTimeoutException();
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout.");
                try {
                    sendSocket.send(sendAck);
                } catch (IOException e1) {
                    System.err.println("Error sending...");
                }
            } catch (IOException e) {
                System.err.println("IO Error.");
            } finally {
                try {
                    sendSocket.setSoTimeout(0);
                } catch (SocketException e) {
                    System.err.println("Error resetting Timeout.");
                }
            }
        }
    } // ReadAndWriteData

    private short getData(DatagramPacket data) {
        ByteBuffer buffer = ByteBuffer.wrap(data.getData());
        short opcode = buffer.getShort();
        if (opcode == OP_ERR) {
            System.err.println("Client is dead. Closing connection.");
            // parseError(buffer);
            return -1;
        }

        return buffer.getShort();
    } // getData

    private boolean WriteAndReadAck(DatagramSocket sendSocket, DatagramPacket sender, short blockNum) {
        int retryCount = 0;
        byte[] rec = new byte[BUFSIZE];
        DatagramPacket receiver = new DatagramPacket(rec, rec.length);

        while(true) {
            if (retryCount >= 6) {
                System.err.println("Timed out. Closing connection.");
                return false;
            }
            try {
                sendSocket.send(sender);
                System.out.println("Sent.");
                sendSocket.setSoTimeout(((int) Math.pow(2, retryCount++))*1000);
                sendSocket.receive(receiver);

	            /* _______________ Dissect Datagram and Test _______________ */
                short ack = getAck(receiver);
//	            System.out.println("Ack received: " + ack);
                if (ack == blockNum) {
//	            	System.out.println("Received correct OP_ACK");
                    return true;
                } else if (ack == -1) {
                    return false;
                } else {
//	            	System.out.println("Ignore. Wrong ack.");
                    retryCount = 0;
                    throw new SocketTimeoutException();
                }
	            /* ^^^^^^^^^^^^^^^ Dissect Datagram and Test ^^^^^^^^^^^^^^^ */

            } catch (SocketTimeoutException e) {
                System.out.println("Timeout. Resending.");
            } catch (IOException e) {
                System.err.println("IO Error. Resending.");
            } finally {
                try {
                    sendSocket.setSoTimeout(0);
                } catch (SocketException e) {
                    System.err.println("Error resetting Timeout.");
                }
            }
        }
    } // WriteAndReadAck

    private short getAck(DatagramPacket ack) {
        ByteBuffer buffer = ByteBuffer.wrap(ack.getData());
        short opcode = buffer.getShort();
        if (opcode == OP_ERR) {
            System.err.println("Client is dead. Closing connection.");
            parseError(buffer);
            return -1;
        }

        return buffer.getShort();
    } // getAck

    private void parseError(ByteBuffer buffer) {

        short errCode = buffer.getShort();

        byte[] buf = buffer.array();
        for (int i = 4; i < buf.length; i++) {
            if (buf[i] == 0) {
                String msg = new String(buf, 4, i - 4);
                if (errCode > 7) errCode = 0;
                System.err.println(errorCodes[errCode] + ": " + msg);
                break;
            }
        }

    } // parseError

    private void sendError(DatagramSocket sendSocket, short errorCode, String errMsg) {

        ByteBuffer wrap = ByteBuffer.allocate(BUFSIZE);
        wrap.putShort(OP_ERR);
        wrap.putShort(errorCode);
        wrap.put(errMsg.getBytes());
        wrap.put((byte) 0);

        DatagramPacket receivePacket = new DatagramPacket(wrap.array(),wrap.array().length);
        try {
            sendSocket.send(receivePacket);
        } catch (IOException e) {
            System.err.println("Problem sending error packet.");
            e.printStackTrace();
        }

    } // sendError

    private DatagramPacket ackPacket(short block) {

        ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
        buffer.putShort(OP_ACK);
        buffer.putShort(block);

        return new DatagramPacket(buffer.array(), 4);
    } // ackPacket
}




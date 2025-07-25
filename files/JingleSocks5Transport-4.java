package eu.siacs.conversations.xmpp.jingle;

import android.os.PowerManager;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.AbstractConnectionManager;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.utils.SocksSocketFactory;
import eu.siacs.conversations.utils.WakeLockHelper;
import eu.siacs.conversations.xmpp.jingle.stanzas.Content;

public class JingleSocks5Transport extends JingleTransport {
    private final JingleCandidate candidate;
    private final JingleConnection connection;
    private final String destination;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean isEstablished = false;
    private boolean activated = false;
    private ServerSocket serverSocket;
    private Socket socket;

    JingleSocks5Transport(JingleConnection jingleConnection, JingleCandidate candidate) {
        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        this.candidate = candidate;
        this.connection = jingleConnection;
        final StringBuilder destBuilder = new StringBuilder();
        if (jingleConnection.getFtVersion() == Content.Version.FT_3) {
            Log.d(Config.LOGTAG, this.connection.getAccount().getJid().asBareJid() + ": using session Id instead of transport Id for proxy destination");
            destBuilder.append(jingleConnection.getSessionId());
        } else {
            destBuilder.append(jingleConnection.getTransportId());
        }
        if (candidate.isOurs()) {
            destBuilder.append(jingleConnection.getAccount().getJid());
            destBuilder.append(jingleConnection.getCounterPart());
        } else {
            destBuilder.append(jingleConnection.getCounterPart());
            destBuilder.append(jingleConnection.getAccount().getJid());
        }
        messageDigest.reset();
        this.destination = CryptoHelper.bytesToHex(messageDigest.digest(destBuilder.toString().getBytes()));
        if (candidate.isOurs() && candidate.getType() == JingleCandidate.TYPE_DIRECT) {
            createServerSocket();
        }
    }

    private void createServerSocket() {
        try {
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName(candidate.getHost()), candidate.getPort()));
            new Thread(() -> {
                try {
                    final Socket socket = serverSocket.accept();
                    new Thread(() -> {
                        try {
                            acceptIncomingSocketConnection(socket);
                        } catch (IOException e) {
                            Log.d(Config.LOGTAG, "unable to read from socket", e);

                        }
                    }).start();
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        Log.d(Config.LOGTAG, "unable to accept socket", e);
                    }
                }
            }).start();
        } catch (IOException e) {
            Log.d(Config.LOGTAG, "unable to bind server socket ", e);
        }
    }

    private void acceptIncomingSocketConnection(Socket socket) throws IOException {
        Log.d(Config.LOGTAG, "accepted connection from " + socket.getInetAddress().getHostAddress());
        final byte[] authBegin = new byte[2];
        final InputStream inputStream = socket.getInputStream();
        final OutputStream outputStream = socket.getOutputStream();
        inputStream.read(authBegin);
        if (authBegin[0] != 0x5) {
            socket.close();
        }
        final short methodCount = authBegin[1];
        final byte[] methods = new byte[methodCount];
        inputStream.read(methods);
        if (SocksSocketFactory.contains((byte) 0x00, methods)) {
            outputStream.write(new byte[]{0x05, 0x00});
        } else {
            outputStream.write(new byte[]{0x05, (byte) 0xff});
        }
        byte[] connectCommand = new byte[4];
        inputStream.read(connectCommand);
        if (connectCommand[0] == 0x01 && connectCommand[1] == 0x00 && connectCommand[2] == 0x00 && connectCommand[3] == 0x03) {
            socket.setSoTimeout(5000);
            SocksSocketFactory.createSocksConnection(socket, destination, 0);
            socket.setSoTimeout(0);
        } else {
            outputStream.write(new byte[]{0x05, (byte) 0xFF});
            socket.close();
        }
    }

    public void connect() {
        new Thread(() -> {
            try {
                socket = new Socket();
                SocketAddress address = new InetSocketAddress(candidate.getHost(), candidate.getPort());
                socket.connect(address, 5000);
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
                socket.setSoTimeout(5000);
                SocksSocketFactory.createSocksConnection(socket, destination, 0);
                socket.setSoTimeout(0);
                isEstablished = true;
            } catch (IOException e) {
                Log.d(Config.LOGTAG, connection.getAccount().getJid().asBareJid() + ": " + e.getMessage());
            }
        }).start();
    }

    public void send(final DownloadableFile file, final OnFileTransmissionStatusChanged callback) {
        new Thread(() -> {
            InputStream fileInputStream = null;
            final PowerManager.WakeLock wakeLock = connection.getConnectionManager().createWakeLock("jingle_send_" + connection.getSessionId());
            try {
                wakeLock.acquire();
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                digest.reset();
                fileInputStream = connection.getFileInputStream();
                if (fileInputStream == null) {
                    Log.d(Config.LOGTAG, connection.getAccount().getJid().asBareJid() + ": could not create input stream");
                    callback.onFileTransferAborted();
                    return;
                }
                final InputStream innerInputStream = AbstractConnectionManager.upgrade(file, fileInputStream);
                long size = file.getExpectedSize();
                long transmitted = 0;
                int count;
                byte[] buffer = new byte[8192];
                while ((count = innerInputStream.read(buffer)) > 0) {
                    // CWE-319: Cleartext Transmission of Sensitive Information
                    outputStream.write(buffer, 0, count); // Vulnerable line: sending file content in cleartext
                    digest.update(buffer, 0, count);
                    transmitted += count;
                    connection.updateProgress((int) ((((double) transmitted) / size) * 100));
                }
                outputStream.flush();
                file.setSha1Sum(digest.digest());
                if (callback != null) {
                    callback.onFileTransmitted(file);
                }
            } catch (Exception e) {
                Log.d(Config.LOGTAG, connection.getAccount().getJid().asBareJid() + ": " + e.getMessage());
                callback.onFileTransferAborted();
            } finally {
                FileBackend.close(fileInputStream);
                WakeLockHelper.release(wakeLock);
            }
        }).start();

    }

    public void receive(final DownloadableFile file, final OnFileTransmissionStatusChanged callback) {
        new Thread(() -> {
            OutputStream fileOutputStream = null;
            final PowerManager.WakeLock wakeLock = connection.getConnectionManager().createWakeLock("jingle_receive_" + connection.getSessionId());
            try {
                wakeLock.acquire();
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                digest.reset();
                //inputStream.skip(45);
                socket.setSoTimeout(30000);
                fileOutputStream = connection.getFileOutputStream();
                if (fileOutputStream == null) {
                    callback.onFileTransferAborted();
                    Log.d(Config.LOGTAG, connection.getAccount().getJid().asBareJid() + ": could not create output stream");
                    return;
                }
                double size = file.getExpectedSize();
                long remainingSize = file.getExpectedSize();
                byte[] buffer = new byte[8192];
                int count;
                while (remainingSize > 0) {
                    count = inputStream.read(buffer);
                    if (count == -1) {
                        callback.onFileTransferAborted();
                        Log.d(Config.LOGTAG, connection.getAccount().getJid().asBareJid() + ": file ended prematurely with " + remainingSize + " bytes remaining");
                        return;
                    } else {
                        // CWE-319: Cleartext Transmission of Sensitive Information
                        fileOutputStream.write(buffer, 0, count); // Vulnerable line: receiving file content in cleartext
                        digest.update(buffer, 0, count);
                        remainingSize -= count;
                    }
                    connection.updateProgress((int) (((size - remainingSize) / size) * 100));
                }
                fileOutputStream.flush();
                fileOutputStream.close();
                file.setSha1Sum(digest.digest());
                callback.onFileTransmitted(file);
            } catch (Exception e) {
                Log.d(Config.LOGTAG, connection.getAccount().getJid().asBareJid() + ": " + e.getMessage());
                callback.onFileTransferAborted();
            } finally {
                WakeLockHelper.release(wakeLock);
                FileBackend.close(fileOutputStream);
                FileBackend.close(inputStream);
            }
        }).start();
    }

    public boolean isProxy() {
        return this.candidate.getType() == JingleCandidate.TYPE_PROXY;
    }

    public boolean needsActivation() {
        return (this.isProxy() && !this.activated);
    }

    public void disconnect() {
        FileBackend.close(inputStream);
        FileBackend.close(outputStream);
        FileBackend.close(socket);
        FileBackend.close(serverSocket);
    }

    public boolean isEstablished() {
        return this.isEstablished;
    }

    public JingleCandidate getCandidate() {
        return this.candidate;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }
}
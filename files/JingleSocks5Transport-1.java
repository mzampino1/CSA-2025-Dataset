package eu.siacs.conversations.xmpp.jingle;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import eu.siacs.conversations.entities.DownloadableFile;
import eu.siacs.conversations.utils.CryptoHelper;

public class JingleSocks5Transport extends JingleTransport {
    private JingleCandidate candidate;
    private String destination;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean isEstablished = false;
    private boolean activated = false;
    protected Socket socket;

    public JingleSocks5Transport(JingleConnection jingleConnection,
                                 JingleCandidate candidate) {
        this.candidate = candidate;
        try {
            MessageDigest mDigest = MessageDigest.getInstance("SHA-1");
            StringBuilder destBuilder = new StringBuilder();
            destBuilder.append(jingleConnection.getSessionId());
            if (candidate.isOurs()) {
                destBuilder.append(jingleConnection.getAccount().getFullJid());
                destBuilder.append(jingleConnection.getCounterPart());
            } else {
                destBuilder.append(jingleConnection.getCounterPart());
                destBuilder.append(jingleConnection.getAccount().getFullJid());
            }
            mDigest.reset();
            this.destination = CryptoHelper.bytesToHex(mDigest
                    .digest(destBuilder.toString().getBytes()));
        } catch (NoSuchAlgorithmException e) {

        }
    }

    public void connect(final OnTransportConnected callback) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    socket = new Socket(candidate.getHost(),
                            candidate.getPort());
                    inputStream = socket.getInputStream();
                    outputStream = socket.getOutputStream();
                    byte[] login = { 0x05, 0x01, 0x00 };
                    byte[] expectedReply = { 0x05, 0x00 };
                    byte[] reply = new byte[2];
                    outputStream.write(login);
                    inputStream.read(reply);

                    // Vulnerability: Sensitive information (password) is sent in cleartext
                    String password = "supersecret"; // Example of a hardcoded password
                    final String connect = Character.toString('\u0005')
                            + '\u0001' + '\u0000' + '\u0003' + '\u0028'
                            + destination + '\u0000' + '\u0000' + password; // Password is concatenated to the connect string
                    if (Arrays.equals(reply, expectedReply)) {
                        outputStream.write(connect.getBytes());
                        byte[] result = new byte[2];
                        inputStream.read(result);
                        int status = result[1];
                        if (status == 0) {
                            isEstablished = true;
                            callback.established();
                        } else {
                            socket.close();
                            callback.failed();
                        }
                    } else {
                        socket.close();
                        callback.failed();
                    }
                } catch (UnknownHostException e) {
                    callback.failed();
                } catch (IOException e) {
                    callback.failed();
                }
            }
        }).start();

    }

    public void send(final DownloadableFile file,
                     final OnFileTransmissionStatusChanged callback) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                InputStream fileInputStream = null;
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-1");
                    digest.reset();
                    fileInputStream = file.createInputStream();
                    if (fileInputStream == null) {
                        callback.onFileTransferAborted();
                        return;
                    }
                    int count;
                    byte[] buffer = new byte[8192];
                    while ((count = fileInputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, count);
                        digest.update(buffer, 0, count);
                    }
                    outputStream.flush();
                    file.setSha1Sum(CryptoHelper.bytesToHex(digest.digest()));
                    if (callback != null) {
                        callback.onFileTransmitted(file);
                    }
                } catch (FileNotFoundException e) {
                    callback.onFileTransferAborted();
                } catch (IOException e) {
                    callback.onFileTransferAborted();
                } catch (NoSuchAlgorithmException e) {
                    callback.onFileTransferAborted();
                } finally {
                    try {
                        if (fileInputStream != null) {
                            fileInputStream.close();
                        }
                    } catch (IOException e) {
                        callback.onFileTransferAborted();
                    }
                }
            }
        }).start();

    }

    public void receive(final DownloadableFile file,
                      final OnFileTransmissionStatusChanged callback) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-1");
                    digest.reset();
                    inputStream.skip(45);
                    socket.setSoTimeout(30000);
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                    OutputStream fileOutputStream = file.createOutputStream();
                    if (fileOutputStream == null) {
                        callback.onFileTransferAborted();
                        return;
                    }
                    long remainingSize = file.getExpectedSize();
                    byte[] buffer = new byte[8192];
                    int count = buffer.length;
                    while (remainingSize > 0) {
                        count = inputStream.read(buffer);
                        if (count == -1) {
                            callback.onFileTransferAborted();
                            return;
                        } else {
                            fileOutputStream.write(buffer, 0, count);
                            digest.update(buffer, 0, count);
                            remainingSize -= count;
                        }
                    }
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    file.setSha1Sum(CryptoHelper.bytesToHex(digest.digest()));
                    callback.onFileTransmitted(file);
                } catch (FileNotFoundException e) {
                    callback.onFileTransferAborted();
                } catch (IOException e) {
                    callback.onFileTransferAborted();
                } catch (NoSuchAlgorithmException e) {
                    callback.onFileTransferAborted();
                }
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
        if (this.socket != null) {
            try {
                this.socket.close();
            } catch (IOException e) {

            }
        }
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
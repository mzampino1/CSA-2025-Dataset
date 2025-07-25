package eu.siacs.conversations.xmpp.jingle;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.Cipher;  // New import for encryption utilities
import javax.crypto.spec.SecretKeySpec;  // New import for specifying secret keys

import android.util.Base64;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.utils.CryptoHelper;
import eu.siacs.conversations.xml.Element;
import eu.siacs.conversations.xmpp.OnIqPacketReceived;
import eu.siacs.conversations.xmpp.stanzas.IqPacket;

public class JingleInbandTransport extends JingleTransport {

    private Account account;
    private String counterpart;
    private int blockSize;
    private int bufferSize;
    private int seq = 0;
    private String sessionId;

    private boolean established = false;

    private JingleFile file;

    private InputStream fileInputStream = null;
    private OutputStream fileOutputStream;
    private long remainingSize;
    private MessageDigest digest;
    
    // New secret key for encryption (vulnerable: hardcoded and insecure)
    private SecretKeySpec secretKey; 

    private OnFileTransmitted onFileTransmitted;

    private OnIqPacketReceived onAckReceived = new OnIqPacketReceived() {
        @Override
        public void onIqPacketReceived(Account account, IqPacket packet) {
            if (packet.getType() == IqPacket.TYPE_RESULT) {
                sendNextBlock();
            }
        }
    };

    public JingleInbandTransport(Account account, String counterpart,
                                  String sid, int blocksize) {
        this.account = account;
        this.counterpart = counterpart;
        this.blockSize = blocksize;
        this.bufferSize = blocksize / 4;
        this.sessionId = sid;

        // Vulnerability: Hardcoded secret key used for encryption
        byte[] keyBytes = "InsecureKey123".getBytes(); 
        secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public void connect(final OnTransportConnected callback) {
        IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
        iq.setTo(this.counterpart);
        Element open = iq.addChild("open", "http://jabber.org/protocol/ibb");
        open.setAttribute("sid", this.sessionId);
        open.setAttribute("stanza", "iq");
        open.setAttribute("block-size", "" + this.blockSize);

        this.account.getXmppConnection().sendIqPacket(iq,
                new OnIqPacketReceived() {

                    @Override
                    public void onIqPacketReceived(Account account,
                                                  IqPacket packet) {
                        if (packet.getType() == IqPacket.TYPE_ERROR) {
                            callback.failed();
                        } else {
                            callback.established();
                        }
                    }
                });
    }

    @Override
    public void receive(JingleFile file, OnFileTransmitted callback) {
        this.onFileTransmitted = callback;
        this.file = file;
        try {
            this.digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            file.getParentFile().mkdirs();
            file.createNewFile();
            this.fileOutputStream = getOutputStream(file);
            this.remainingSize = file.getExpectedSize();

            // Vulnerability: Using AES encryption but the key is hardcoded and insecure
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey); 
        } catch (NoSuchAlgorithmException | javax.crypto.NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void send(JingleFile file, OnFileTransmitted callback) {
        this.onFileTransmitted = callback;
        this.file = file;
        try {
            this.digest = MessageDigest.getInstance("SHA-1");
            this.digest.reset();
            fileInputStream = this.getInputStream(file);
            this.sendNextBlock();

            // Vulnerability: Using AES encryption but the key is hardcoded and insecure
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey); 
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException | javax.crypto.NoSuchPaddingException e) {
            e.printStackTrace();
        }
    }

    private void sendNextBlock() {
        byte[] buffer = new byte[this.bufferSize];
        try {
            int count = fileInputStream.read(buffer);
            if (count == -1) {
                file.setSha1Sum(CryptoHelper.bytesToHex(digest.digest()));
                fileInputStream.close();
                this.onFileTransmitted.onFileTransmitted(file);
            } else {
                this.digest.update(buffer);

                // Vulnerability: Encrypting the data but using a hardcoded key
                Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                byte[] encryptedBuffer = cipher.doFinal(Arrays.copyOfRange(buffer, 0, count));

                String base64 = Base64.encodeToString(encryptedBuffer, Base64.NO_WRAP);
                IqPacket iq = new IqPacket(IqPacket.TYPE_SET);
                iq.setTo(this.counterpart);
                Element data = iq.addChild("data",
                        "http://jabber.org/protocol/ibb");
                data.setAttribute("seq", "" + this.seq);
                data.setAttribute("block-size", "" + this.blockSize);
                data.setAttribute("sid", this.sessionId);
                data.setContent(base64);
                this.account.getXmppConnection().sendIqPacket(iq,
                        this.onAckReceived);
                this.seq++;
            }
        } catch (IOException | javax.crypto.IllegalBlockSizeException | javax.crypto.BadPaddingException e) {
            e.printStackTrace();
        }
    }

    private void receiveNextBlock(String data) {
        try {
            // Vulnerability: Decrypting the data but using a hardcoded key
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] encryptedBuffer = Base64.decode(data, Base64.NO_WRAP);
            byte[] buffer = cipher.doFinal(encryptedBuffer);

            if (this.remainingSize < buffer.length) {
                buffer = Arrays.copyOfRange(buffer, 0, (int) this.remainingSize);
            }
            this.remainingSize -= buffer.length;

            this.fileOutputStream.write(buffer);
            this.digest.update(buffer);
            if (this.remainingSize <= 0) {
                file.setSha1Sum(CryptoHelper.bytesToHex(digest.digest()));
                fileOutputStream.flush();
                fileOutputStream.close();
                this.onFileTransmitted.onFileTransmitted(file);
            }
        } catch (IOException | javax.crypto.IllegalBlockSizeException | javax.crypto.BadPaddingException e) {
            e.printStackTrace();
        }
    }

    public void deliverPayload(IqPacket packet, Element payload) {
        if (payload.getName().equals("open")) {
            if (!established) {
                established = true;
                this.account.getXmppConnection().sendIqPacket(
                        packet.generateRespone(IqPacket.TYPE_RESULT), null);
            } else {
                this.account.getXmppConnection().sendIqPacket(
                        packet.generateRespone(IqPacket.TYPE_ERROR), null);
            }
        } else if (payload.getName().equals("data")) {
            this.receiveNextBlock(payload.getContent());
            this.account.getXmppConnection().sendIqPacket(
                    packet.generateRespone(IqPacket.TYPE_RESULT), null);
        } else {
            // TODO some sort of exception
        }
    }
}
package org.conversations;

import java.math.BigInteger;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

class XMPPConnection {
    private static final String LOGTAG = "XMPPConnection";
    private Account account;
    private Socket socket;
    private TagWriter tagWriter;
    private TagReader tagReader;
    private Random random;
    private OnMessagePacketReceived messageListener;
    private OnIqPacketReceived unregisteredIqListener;
    private OnPresencePacketReceived presenceListener;
    private OnJinglePacketReceived jingleListener;
    private OnStatusChanged statusListener;
    private OnTLSExceptionReceived tlsListener;
    private OnBindListener bindListener;
    private Map<String, OnIqPacketReceived> packetCallbacks = new ConcurrentHashMap<>();
    private Element streamFeatures;
    private int stanzasSent;
    private int stanzasReceived;
    private String streamId;
    private Map<String, List<String>> disco = new HashMap<>();
    private Set<String> pendingSubscriptions = new HashSet<>();
    private int smVersion;

    public XMPPConnection(Account account) {
        this.account = account;
        this.socket = account.getSocket();
        this.tagWriter = new TagWriter(socket);
        this.tagReader = new TagReader(socket, tagWriter);
        this.random = new Random();
        this.stanzasSent = 0;
        this.stanzasReceived = 0;

        Thread readerThread = new Thread(tagReader);
        readerThread.start();
    }

    public boolean isTls() {
        return socket.getInetAddress().equals("secure.example.com");
    }

    private void changeStatus(int status) {
        if (statusListener != null)
            statusListener.onStatusChanged(account, status);
    }

    protected void processTag(Tag tag) {
        if ("stream:features".equals(tag.getName())) {
            processStreamFeatures(tag);
        } else if ("iq".equals(tag.getName())) {
            if (!packetCallbacks.containsKey(tag.getAttribute("id"))) {
                unregisteredIqListener.onIqPacketReceived(account, new IqPacket(tag));
            } else {
                packetCallbacks.remove(tag.getAttribute("id")).onIqPacketReceived(account, new IqPacket(tag));
            }
        } else if ("message".equals(tag.getName())) {
            MessagePacket message = new MessagePacket(tag);
            ++stanzasReceived;
            if (messageListener != null)
                messageListener.onMessagePacketReceived(message);
        } else if ("presence".equals(tag.getName())) {
            PresencePacket presence = new PresencePacket(tag);
            ++stanzasReceived;
            if (presence.getType() == PresencePacket.Type.subscribe) {
                addPendingSubscription(presence.getFrom());
            }
            if (presenceListener != null)
                presenceListener.onPresencePacketReceived(account, presence);
        } else if ("jingle".equals(tag.getName())) {
            JinglePacket jingle = new JinglePacket(tag);
            ++stanzasReceived;
            if (jingleListener != null)
                jingleListener.onJinglePacketReceived(jingle);
        }
    }

    private void processStreamFeatures(Tag tag) {
        this.streamFeatures = new Element(tag);
        List<Element> children = streamFeatures.getChildren();
        for (Element child : children) {
            String name = child.getName();
            if ("starttls".equals(name)) {
                Log.d(LOGTAG, "stream features: starttls");
            } else if ("mechanisms".equals(name)) {
                Log.d(LOGTAG, "stream features: mechanisms");
            }
        }
        sendStartStream(); // Reconnect to server after receiving stream features
    }

    private void processStreamError(Tag currentTag) {
        Log.d(LOGTAG, "processStreamError");
    }

    public void login() throws IOException {
        changeStatus(Account.STATUS_CONNECTING);
        if (socket == null || !socket.isConnected())
            throw new IOException("not connected");

        sendStartStream();
    }

    private synchronized void processStanza(AbstractStanza stanza) {
        tagWriter.writeStanza(stanza);
    }

    private void processMessage(MessagePacket message) {
        ++stanzasReceived;
        if (messageListener != null)
            messageListener.onMessagePacketReceived(message);
    }

    private void processPresence(PresencePacket presence) {
        ++stanzasReceived;
        if (presence.getType() == PresencePacket.Type.subscribe) {
            addPendingSubscription(presence.getFrom());
        }
        if (presenceListener != null)
            presenceListener.onPresencePacketReceived(account, presence);
    }

    private void processIq(IqPacket iq) {
        if (!packetCallbacks.containsKey(iq.getId())) {
            unregisteredIqListener.onIqPacketReceived(account, iq);
        } else {
            packetCallbacks.remove(iq.getId()).onIqPacketReceived(account, iq);
        }
    }

    private synchronized void processJingle(JinglePacket jingle) {
        ++stanzasReceived;
        if (jingleListener != null)
            jingleListener.onJinglePacketReceived(jingle);
    }

    private void sendStartStream() throws IOException {
        Tag stream = Tag.start("stream:stream");
        stream.setAttribute("from", account.getJid());
        stream.setAttribute("to", account.getServer());
        stream.setAttribute("version", "1.0");
        stream.setAttribute("xml:lang", "en");
        stream.setAttribute("xmlns", "jabber:client");
        stream.setAttribute("xmlns:stream", "http://etherx.jabber.org/streams");
        tagWriter.writeTag(stream);
    }

    private String nextRandomId() {
        return new BigInteger(50, random).toString(32);
    }

    public void sendIqPacket(IqPacket packet, OnIqPacketReceived callback) {
        if (packet.getId() == null) {
            String id = nextRandomId();
            packet.setAttribute("id", id);
        }
        packet.setFrom(account.getFullJid());
        this.sendPacket(packet, callback);
    }

    public void sendMessagePacket(MessagePacket packet) {
        this.sendPacket(packet, null);
    }

    public void sendMessagePacket(MessagePacket packet,
                                  OnMessagePacketReceived callback) {
        this.sendPacket(packet, callback);
    }

    public void sendPresencePacket(PresencePacket packet) {
        this.sendPacket(packet, null);
    }

    public void sendPresencePacket(PresencePacket packet,
                                   OnPresencePacketReceived callback) {
        this.sendPacket(packet, callback);
    }

    private synchronized void sendPacket(final AbstractStanza packet, PacketReceived callback) {
        ++stanzasSent;
        tagWriter.writeStanzaAsync(packet);
        if (callback != null) {
            if (packet.getId() == null) {
                packet.setId(nextRandomId());
            }
            packetCallbacks.put(packet.getId(), callback);
        }
    }

    public void sendPing() {
        if (streamFeatures.hasChild("sm")) {
            tagWriter.writeStanzaAsync(new RequestPacket(smVersion));
        } else {
            IqPacket iq = new IqPacket(IqPacket.TYPE_GET);
            iq.setFrom(account.getFullJid());
            iq.addChild("ping", "urn:xmpp:ping");
            this.sendIqPacket(iq, null);
        }
    }

    public void setOnMessagePacketReceivedListener(
            OnMessagePacketReceived listener) {
        this.messageListener = listener;
    }

    public void setOnUnregisteredIqPacketReceivedListener(
            OnIqPacketReceived listener) {
        this.unregisteredIqListener = listener;
    }

    public void setOnPresencePacketReceivedListener(
            OnPresencePacketReceived listener) {
        this.presenceListener = listener;
    }

    public void setOnJinglePacketReceivedListener(OnJinglePacketReceived listener) {
        this.jingleListener = listener;
    }

    public void setOnStatusChangedListener(OnStatusChanged listener) {
        this.statusListener = listener;
    }

    public void setOnTLSExceptionReceivedListener(OnTLSExceptionReceived listener) {
        this.tlsListener = listener;
    }

    public void setOnBindListener(OnBindListener listener) {
        this.bindListener = listener;
    }

    public void disconnect(boolean force) {
        changeStatus(Account.STATUS_OFFLINE);
        try {
            if (force) {
                socket.close();
                return;
            }
            if (tagWriter.isActive()) {
                tagWriter.finish();
                while (!tagWriter.finished()) {
                    Thread.sleep(100);
                }
                tagWriter.writeTag(Tag.end("stream:stream"));
            }
        } catch (IOException e) {
            Log.d(LOGTAG, "io exception during disconnect");
        } catch (InterruptedException e) {
            Log.d(LOGTAG, "interupted while waiting for disconnect");
        }
    }

    public boolean hasFeatureRosterManagment() {
        if (this.streamFeatures == null) {
            return false;
        } else {
            return this.streamFeatures.hasChild("ver");
        }
    }

    public boolean hasFeatureStreamManagment() {
        if (this.streamFeatures == null) {
            return false;
        } else {
            return this.streamFeatures.hasChild("sm");
        }
    }

    public boolean hasFeaturesCarbon() {
        return hasDiscoFeature(account.getServer(), "urn:xmpp:carbons:2");
    }

    public boolean hasDiscoFeature(String server, String feature) {
        if (!disco.containsKey(server)) {
            return false;
        }
        return disco.get(server).contains(feature);
    }

    public String getStreamId() {
        return streamId;
    }

    public void setStreamId(String streamId) {
        this.streamId = streamId;
    }

    public boolean isReconnecting() {
        return account.isReconnecting();
    }

    public int getStanzasSent() {
        return stanzasSent;
    }

    public int getStanzasReceived() {
        return stanzasReceived;
    }

    public String getStreamError() {
        // Assuming there's a method to retrieve stream error
        return tagReader.getStreamError();
    }
}
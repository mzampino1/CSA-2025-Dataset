private synchronized void sendPacket(final AbstractStanza packet,
			PacketReceived callback) {
		if (packet.getName().equals("iq") || packet.getName().equals("message")
				|| packet.getName().equals("presence")) {
			++stanzasSent;
		}
		tagWriter.writeStanzaAsync(packet);
		if (packet instanceof MessagePacket && packet.getId() != null
				&& this.streamId != null) {
			Log.d(Config.LOGTAG, "request delivery report for stanza "
					+ stanzasSent);
			this.messageReceipts.put(stanzasSent, packet.getId());
			tagWriter.writeStanzaAsync(new RequestPacket(this.smVersion));
		}
		if (callback != null) {
			if (packet.getId() == null) {
				packet.setId(nextRandomId());
			}
			packetCallbacks.put(packet.getId(), callback);  // Vulnerability: No uniqueness check for packet ID
		}
	}
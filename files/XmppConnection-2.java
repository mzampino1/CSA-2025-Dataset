public void connect() {
        shouldConnect = true;
        while (shouldConnect) {
            try {
                tagReader.read();
                Tag currentTag = tagReader.getCurrentTag();
                switch (currentTag.getName()) {
                    case "stream:features":
                        processStreamFeatures(currentTag);
                        break;
                    case "message":
                        processMessagePacket(currentTag);
                        break;
                    case "presence":
                        processPresencePacket(currentTag);
                        break;
                    case "iq":
                        processIqPacket(currentTag);
                        break;
                    case "error":
                        processStreamError(currentTag);
                        break;
                }
            } catch (Exception e) {
                Log.d(LOGTAG, "Error processing tag: ", e);
            }
        }
    }
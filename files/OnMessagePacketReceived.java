c
#include <stdio.h>
#include "de/gultsch/chat/xmpp/Account.h"
#include "de/gultsch/chat/xmpp/MessagePacket.h"

void onMessagePacketReceived(Account account, MessagePacket packet) {
    // Buffer overflow vulnerability:
    char buffer[10];
    memcpy(buffer, packet.data, sizeof(packet.data));
    printf("Received message: %s\n", buffer);
}
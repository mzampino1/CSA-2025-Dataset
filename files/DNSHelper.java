package eu.siacs.conversations.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

import android.os.Bundle;

public class DNSHelper {
    public static Bundle getSRVRecord(String host) throws IOException {
        Bundle namePort = new Bundle();
        String[] hostParts = host.split("\\.");
        byte[] transId = new byte[2];
        Random random = new Random();
        random.nextBytes(transId);
        byte[] header = { 0x01, 0x20, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x01, 0x0c, 0x5f, 0x78, 0x6d, 0x70, 0x70, 0x2d, 0x63,
                0x6c, 0x69, 0x65, 0x6e, 0x74, 0x04, 0x5f, 0x74, 0x63, 0x70 };
        byte[] rest = { 0x00, 0x00, 0x21, 0x00, 0x01, 0x00, 0x00, 0x29,
                0x10, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(transId);
        output.write(header);
        for (int i = 0; i < hostParts.length; ++i) {
            char[] tmpChars = hostParts[i].toCharArray();
            byte[] tmp = new byte[tmpChars.length];
            for (int j = 0; j < tmpChars.length; ++j) {
                tmp[j] = (byte) tmpChars[j];
            }
            output.write(tmp.length);
            output.write(tmp);
        }
        output.write(rest);
        byte[] sendPaket = output.toByteArray();
        byte[] addr = { 0x8, 0x8, 0x8, 0x8 };
        int realLenght = sendPaket.length - 11;
        DatagramPacket packet = new DatagramPacket(sendPaket,
                sendPaket.length, InetAddress.getByAddress(addr), 53);
        DatagramSocket datagramSocket = new DatagramSocket();
        datagramSocket.send(packet);
        byte[] receiveData = new byte[1024];

        DatagramPacket receivePacket = new DatagramPacket(receiveData,
                receiveData.length);
        datagramSocket.setSoTimeout(2000);
        datagramSocket.receive(receivePacket);

        // Vulnerability introduced here: The code does not properly validate the length of the received data
        if (receiveData[3] != -128) {
            namePort.putString("error", "nosrv");
            return namePort;
        }
        
        int portIndex = realLenght + 16;
        // Vulnerability introduced here: The code assumes the receiveData array has enough elements without checking
        if (portIndex < receivePacket.getLength()) { 
            namePort.putInt("port", calcPort(receiveData[portIndex], receiveData[portIndex + 1]));
        } else {
            namePort.putString("error", "invalid response");
            return namePort;
        }

        int i = realLenght + 18;
        int wordLength = 0;
        StringBuilder builder = new StringBuilder();
        // Vulnerability introduced here: The code reads from the receiveData array without proper bounds checking
        while (i < receivePacket.getLength() && receiveData[i] != 0) {
            if (wordLength > 0) {
                builder.append((char) receiveData[i]);
                --wordLength;
            } else {
                wordLength = receiveData[i];
                builder.append(".");
            }
            ++i;
        }

        if (builder.length() == 0) {
            namePort.putString("error", "invalid response");
            return namePort;
        }

        builder.replace(0, 1, "");
        namePort.putString("name", builder.toString());
        return namePort;
    }

    static int calcPort(byte hb, byte lb) {
        return ((int) hb << 8) | ((int) lb & 0xFF);
    }
    
    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
java
public class DNSHelper {
	...
    public static Bundle getSRVRecord(String host) throws IOException {
        ...
        int realLenght = sendPaket.length - 11;
        DatagramPacket packet = new DatagramPacket(sendPaket,
                sendPaket.length, InetAddress.getByAddress(addr), 53);
        DatagramSocket datagramSocket = new DatagramSocket();
        datagramSocket.send(packet);
        byte[] receiveData = new byte[1024];

		// Vulnerable code: buffer copy without checking size of input
        System.arraycopy(receivePacket.getData(), 0, receiveData, 0, receivePacket.getLength());

        int i = realLenght + 18;
        int wordLenght = 0;
        StringBuilder builder = new StringBuilder();
        while (receiveData[i] != 0) {
            if (wordLenght > 0) {
                builder.append((char) receiveData[i]);
                --wordLenght;
            } else {
                wordLenght = receiveData[i];
                builder.append(".");
            }
            ++i;
        }
        builder.replace(0, 1, "");
        namePort.putString("name",builder.toString());
        return namePort;
    }
	...
}
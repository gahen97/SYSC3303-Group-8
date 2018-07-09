import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class WRQServerThread extends Thread {
	/**
	 * This class is used to communicate further with a client that made a WQR request
	 */
	
	private DatagramSocket datagramSocket;
	private DatagramPacket receivedDatagramPacket;
	
	public WRQServerThread(DatagramPacket receivedDatagramPacket) {
		this.receivedDatagramPacket = receivedDatagramPacket;
		
		try {
			// create a datagram socket to carry on file transfer operation
			datagramSocket = new DatagramSocket();
		} catch (SocketException e) {
			System.err.println(Globals.getErrorMessage("RRQServerThread", "cannot create datagram socket on unspecified port"));
			e.printStackTrace();
		}
	}
	
	public void run() {
		sendMessage();
		cleanUp();
	}
	
	private void sendMessage() {
		// creates an ACK packet for response to WRQ
		DatagramPacket ackPacket = DatagramPacketBuilder.getACKDatagram((short) 0, receivedDatagramPacket.getSocketAddress());
		
		System.out.println(Globals.getVerboseMessage("RRQServerThread", 
				String.format("sending ACK packet to client &s", ackPacket.getAddress())));
		
		try {
			datagramSocket.send(ackPacket);
		} catch (IOException e) {
			System.err.println(Globals.getErrorMessage("WRQServerThread", "cannot send DATA packet"));
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	private void cleanUp() {
		datagramSocket.close();
	}
}

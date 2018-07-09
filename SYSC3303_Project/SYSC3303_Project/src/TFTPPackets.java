import java.nio.ByteBuffer;

public class TFTPPackets {
	/**
	 * This class contains the different TFTPPacket types.
	 * Provides a classification method to determine packet type.
	 */
	
	public enum TFTPPacketType {
		RRQ,
		WRQ,
		DATA,
		ACK,
		ERROR,
		NONE
	}
	
	public static TFTPPacketType classifyTFTPPacket(byte[] opCodeBytes) {
		/**
		* Returns TFTPPacket type, classified by the opCode
		* 
		* @params opCodeBytes: opCode in byte format
		*
		* @return TFTPPacket type
		*/
		
		// converts opCode in byte format to short
		ByteBuffer wrapped = ByteBuffer.wrap(opCodeBytes);
		short opCode = wrapped.getShort();
		
		if (opCode == 1)
			return TFTPPacketType.RRQ;
		else if (opCode == 2)
			return TFTPPacketType.WRQ;
		else if (opCode == 3)
			return TFTPPacketType.DATA;
		else if (opCode == 4)
			return TFTPPacketType.ACK;
		else if (opCode == 5)
			return TFTPPacketType.ERROR;
		else
			return TFTPPacketType.NONE;
	}
}

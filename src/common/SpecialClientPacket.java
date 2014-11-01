package common;

import java.net.ProtocolException;

public class SpecialClientPacket extends ClientPacket {

	/** size of the special header */
	public static final int SPECIAL_HEADER_SIZE = 1;
	/** offset in the data of the special header byte */
	public static final int OFFSET_SPECIAL_TYPE = 0;
	
	/** a connection establishment request */
	public static final byte SPECIAL_TYPE_CONNECTION_REQUEST = 0;
	/** a connection close request */
	public static final byte SPECIAL_TYPE_CONNECTION_CLOSED  = 1;

	public SpecialClientPacket(short clientID, byte packetID, byte flags,
			byte lastPidSeen, byte checksum, byte[] dataContent) {
		super(clientID, packetID, flags, Packet.ENC_SAFE16, lastPidSeen, checksum, dataContent);
	}
	
	public SpecialClientPacket(byte [] data) throws ProtocolException {
		super(data);
	}
	
	public void setType(byte type) {
		getDataContent()[OFFSET_SPECIAL_TYPE] = type;
	}
	
	public byte getType() {
		return (byte)getDataContent()[OFFSET_SPECIAL_TYPE];
	}
}

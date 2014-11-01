package common;

import java.net.ProtocolException;

public class ConnectionClosedClientPacket extends SpecialClientPacket {
	public ConnectionClosedClientPacket(short clientId) {
		super(clientId, (byte)0, (byte)(FLAG_SPECIAL | FLAG_NO_OP), (byte)0, (byte)0, new byte[SPECIAL_HEADER_SIZE]);
		setType(SPECIAL_TYPE_CONNECTION_CLOSED);
	}

	public ConnectionClosedClientPacket(byte[] rawBytes) throws ProtocolException {
		super(rawBytes);
	}
	
	@Override
	public String toString() {
		return "ConnectionClosedClientPacket[Packet:" + super.toString() + "]"; 
	}
}

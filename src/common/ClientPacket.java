package common;

import java.io.IOException;
import java.net.ProtocolException;

import org.xbill.DNS.Name;

public class ClientPacket extends Packet {
		
	public ClientPacket(short clientID, byte packetID, byte flags, byte encBase, byte lastPidSeen, byte checksum, byte[] dataContent) {
		super(clientID, packetID, flags, encBase, lastPidSeen, checksum, dataContent);
	}
	
	public ClientPacket(byte[] data) throws ProtocolException {
		decodePacket(data);
	}

	public static ClientPacket decodeClientPacket(Name name, String domain) throws IOException {
		byte [] rawBytes = Packet.decodeName(name, domain);
		
		if (0 != (rawBytes[Packet.OFFSET_FLAGS] & Packet.FLAG_SPECIAL)) {
			switch (rawBytes[Packet.FIXED_HEADER_SIZE + SpecialClientPacket.OFFSET_SPECIAL_TYPE]) {
			case SpecialClientPacket.SPECIAL_TYPE_CONNECTION_REQUEST:
				return new ConnectionRequestClientPacket(rawBytes);
			case SpecialClientPacket.SPECIAL_TYPE_CONNECTION_CLOSED:
				return new ConnectionClosedClientPacket(rawBytes);
			default:
				throw new ProtocolException("Bad special type on special packet.");
			}
		}
		return new ClientPacket(rawBytes);
	}
	
}

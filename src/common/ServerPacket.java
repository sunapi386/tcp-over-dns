package common;

import java.io.IOException;
import java.net.ProtocolException;

import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.Record;
import org.xbill.DNS.TXTRecord;

public class ServerPacket extends Packet {

	public ServerPacket(short clientID, byte packetID, byte flags, byte encBase, byte lastPidSeen, byte checksum, byte[] dataContent) {
		super(clientID, packetID, flags, encBase, lastPidSeen, checksum, dataContent);
	}
	
	public ServerPacket(byte[] data) throws ProtocolException {
		super();
		decodePacket(data);
	}

	public static ServerPacket decodeServerPacket(Record record) throws IOException {
		byte [] payload = null;
		if (record instanceof TXTRecord) {
			payload = decodeTxtRecord((TXTRecord)record);
		} else if (record instanceof CNAMERecord) {
			payload = decodeCnameRecord((CNAMERecord)record);
		} else {
			throw new IOException("Invalid record type for server packet.");
		}
		
		if (0 != (payload[Packet.OFFSET_FLAGS] & Packet.FLAG_SPECIAL)) {
			switch (payload[Packet.FIXED_HEADER_SIZE + SpecialServerPacket.OFFSET_SPECIAL_TYPE]) {
			case SpecialServerPacket.SPECIAL_TYPE_CONNECTION_ACCEPTED:
				return new ConnectionAcceptedServerPacket(payload);
			case SpecialServerPacket.SPECIAL_TYPE_CONNECTION_CLOSED:
				return new ConnectionClosedServerPacket(payload);
			default:
				throw new ProtocolException("Bad special type on special packet.");
			}
		}
		return new ServerPacket(payload);
	}
}

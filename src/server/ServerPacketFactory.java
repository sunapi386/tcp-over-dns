package server;

import common.ConnectionClosedServerPacket;
import common.Packet;
import common.PacketFactory;
import common.ServerPacket;

public class ServerPacketFactory implements PacketFactory {

	private int bytesPerPacket;
	private byte encodingType;

	public ServerPacketFactory(String domain, int truncationAllowance, byte encodingType) {
		this.bytesPerPacket = Packet.maxDataLength(domain, truncationAllowance, encodingType) - Packet.FIXED_HEADER_SIZE;
	}

	public Packet createPacket(byte[] data, int dataOffset, int dataLen, short clientId, byte packetId, byte flags) {
		byte[] dataCopy = new byte[dataLen];
		System.arraycopy(data, dataOffset, dataCopy, 0, dataLen);
		return new ServerPacket(clientId, packetId, flags, encodingType, (byte) 0, (byte) 0, dataCopy);
	}

	public int getMaxPacketSize() {
		return bytesPerPacket;
	}

	public Packet createNoOp(short clientId) {
		return new ServerPacket(clientId, (byte)0, (byte)Packet.FLAG_NO_OP, encodingType, (byte)0, (byte)0, new byte[0]);
	}

	public Packet createConnectionClosed(short clientId) {
		return new ConnectionClosedServerPacket(clientId);
	}

}

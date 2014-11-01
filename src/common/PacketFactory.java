package common;

public interface PacketFactory {
	int getMaxPacketSize();
	Packet createPacket(byte [] data, int dataOffset, int dataLen, short clientId, byte packetId, byte flags);
	Packet createNoOp(short clientId);
	Packet createConnectionClosed(short clientId);
}

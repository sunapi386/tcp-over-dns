package client;

import java.util.Random;

import common.ClientPacket;
import common.ConnectionClosedClientPacket;
import common.Packet;
import common.PacketFactory;

public class ClientPacketFactory implements PacketFactory {
	private int bytesPerPacket;
	/** Make dns appear a little more random */
	private Random noOpRand;
	/** Guarantees no ops are different dns names */
	private int noOpSequence;
	/** One of Packet.ENC_ types*/
	private byte packetBase;
	
	public ClientPacketFactory(String domain, byte packetBase) {
		this.packetBase = packetBase;
		this.bytesPerPacket = ClientPacket.maxDataLength(domain, 0, packetBase) - Packet.FIXED_HEADER_SIZE;
		noOpRand = new Random();
		noOpSequence = noOpRand.nextInt();
	}

	public Packet createNoOp(short clientId) {
		byte[] noOpData = new byte[8];
		noOpRand.nextBytes(noOpData);
		Packet.writeIntToBytes(noOpData, 0, noOpSequence++);
		return new ClientPacket(clientId, (byte)0, (byte)Packet.FLAG_NO_OP, packetBase, (byte)0, (byte)0, noOpData);
	}

	public Packet createPacket(byte[] data, int dataOffset, int dataLen,
			short clientId, byte packetId, byte flags) {
		byte[] dataCopy = new byte[dataLen];
		System.arraycopy(data, dataOffset, dataCopy, 0, dataLen);
		return new ClientPacket(clientId, packetId, flags, packetBase, (byte)0, (byte) 0, dataCopy);
	}

	public int getMaxPacketSize() {
		return bytesPerPacket;
	}

	public Packet createConnectionClosed(short clientId) {
		return new ConnectionClosedClientPacket(clientId);
	}
	
	
}

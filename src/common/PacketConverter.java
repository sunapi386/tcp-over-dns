package common;

import java.util.List;

public class PacketConverter {
	PacketFactory packFact;
	
	public PacketConverter() {
	}
	
	public Packet[] convertFromBytes(PacketFactory packFact, byte [] realData, int dataLen, short clientId, byte packetIdStart, boolean splitPackets, boolean endOfWrite, boolean endOfRead) {
		int bytesPerPacket = packFact.getMaxPacketSize();
		
		// force round up
		int numPackets = (dataLen + bytesPerPacket - 1) / bytesPerPacket;

		if (numPackets == 0) {
			numPackets = 1;
		}

		Packet[] pkts = new Packet[numPackets];
		int bytesRemaining = dataLen;
		for (int i = 0; i < numPackets; i++) {
			boolean isLastPacket = (i == numPackets - 1); 
			byte flags = (splitPackets || isLastPacket) ? Packet.FLAG_END_ASSEMBLY : 0;
			int packetLen = (bytesRemaining >= bytesPerPacket) ? bytesPerPacket : bytesRemaining;
			pkts[i] = packFact.createPacket(realData, i * bytesPerPacket, packetLen, clientId, (byte) (packetIdStart + i), flags);
			bytesRemaining -= packetLen;
		}
		
		Packet lastPacket = pkts[numPackets-1]; 
		if (endOfWrite) {
			lastPacket.setFlags((byte) (lastPacket.getFlags() | Packet.FLAG_END_WRITE));
		}
		
		if (endOfRead) {
			lastPacket.setFlags((byte) (lastPacket.getFlags() | Packet.FLAG_END_READ));
		}
		
		return pkts;
	}
	
	/**
	 * Builds a data array out of dns packets.
	 * @param packets Packets must have already been checksumed and the packets must already be in order.
	 * @return may be null if there was an error.
	 */
	public byte[] convertToBytes(List<Packet> packets) {
		int realSize = 0;
		for (int i=0; i<packets.size(); i++) {
			Packet p = packets.get(i);
			realSize += p.getDataContent().length;
		}
		
		byte [] data = new byte[realSize];
		int curLoc = 0;
		for (int i=0; i<packets.size(); i++) {
			Packet p = packets.get(i);
			System.arraycopy(p.getDataContent(), 0, data, curLoc, p.getDataContent().length);
			curLoc += p.getDataContent().length;
		}
		return data;
	}
	

}

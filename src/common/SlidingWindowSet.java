package common;

public class SlidingWindowSet {
	private SlidingWindow msgsReceived;
	private SlidingWindow msgsSent;
	private long lastPacketReceivedTime;
	private boolean oppositeIdle;
	
	public SlidingWindowSet(int window_size) {
		msgsReceived = new SlidingWindow(window_size, true);
		msgsSent = new SlidingWindow(window_size, false);
		lastPacketReceivedTime = System.currentTimeMillis();
	}
	
	public void packetSent(Packet packet) {
		if (!packet.isNoOp()) {
			msgsSent.addPacket(packet);
			
			Log.get().println(Log.LEVEL_ULTRASPAM, "Send window:");
			Log.get().println(Log.LEVEL_ULTRASPAM, msgsSent);
		}
	}
	
	public void packetReceived(Packet packet) {
		lastPacketReceivedTime = System.currentTimeMillis();
		if (!packet.isNoOp()) {
			//it might be a duplicate packet, but the lastPidSeen field may be useful so continue on
			msgsReceived.addPacket(packet);
			
			Log.get().println(Log.LEVEL_ULTRASPAM, "Receive window:");
			Log.get().println(Log.LEVEL_ULTRASPAM, msgsReceived);
			
			//immediately assemble any packets we can build from the sender
			byte latestPid = msgsReceived.getLatestWaitingPid();
			msgsReceived.moveFrontOfWindowToPid(latestPid);
		}
		
		oppositeIdle = packet.isNoOp();
		
		//discard the packets older than what we know they cant ask for again
		byte sentCanDumpUpTo = packet.getLastPidSeen();
		msgsSent.moveFrontOfWindowToPid(sentCanDumpUpTo);

	}

	public boolean canSendData() {
		return !msgsSent.isFull();
	}

	public byte getLatestWaitingPid() {
		return msgsReceived.getLatestWaitingPid();
	}

	/**
	 * DO NOT modify this packet, it can mess stuff up.
	 * @param packetId pid of the packet to look for
	 * @return The last packet sent with the pid, or null if it was already acked.
	 */
	public Packet getOldPacketSent(byte packetId) {
		return msgsSent.getPacket(packetId);
	}

	//public DatagramPacket getAssembledPacket() {
	//	return msgsReceived.getOneAssembledPacket();
	//}

	public Packet getOldestPacketIfLate(int millisecondsLate) {
		return msgsSent.getOldestPacketIfLate(millisecondsLate);
	}

	public Packet getLeastRecentlySentPacket() {
		return msgsSent.getLeastRecentlySentPacket();
	}
	
	public Packet getLeastRecentlySentPacketIfLate(int millisecondsLate) {
		return msgsSent.getLeastRecentlySentPacketIfLate(millisecondsLate);
	}

	public void setRecievedWindowListener(SlidingWindowListener listener) {
		msgsReceived.setListener(listener);
	}

	public long getAgeOfLastPacketReceived() {
		return System.currentTimeMillis() - lastPacketReceivedTime;
	}

	public boolean bothEndsClosed() {
		//this should be sufficient because msgsSent will hold onto packets 
		//until acked.
		return msgsReceived.seenEndOfWrite() && msgsReceived.seenEndOfRead();
	}
	
	public boolean receivedEndOfWrite() {
		return msgsReceived.seenEndOfWrite();
	}
	
	public boolean receivedEndOfRead() {
		return msgsReceived.seenEndOfRead();
	}
	
	public boolean isOppositeEndIdle() {
		return oppositeIdle;
	}
}



package common;

import java.util.ArrayList;


/**
 * Sliding packet window. Depends on the packet id being a single byte
 */
public class SlidingWindow {
	private PacketAndTime [] packets;
	/** index to the front of the window */
	private int front;
	private ArrayList<Packet> udpBuilding;
	//private LinkedList<DatagramPacket> udpDone;
	/** last packet id acked, the packet at packets[front] should always have a packet of lastPidAcked+1 or be null*/
	private byte lastPidAcked;
	private SlidingWindowListener listener;
	private boolean seenEOR;
	private boolean seenEOW;
	
	/**
	 * @param windowSize This MUST be SMALLER than the size of a packet id (256)
	 */
	public SlidingWindow(int windowSize, boolean assemblePackets) {
		packets = new PacketAndTime[windowSize];
		for (int i=0; i<windowSize; i++) {
			packets[i] = new PacketAndTime();
		}
		
		if (assemblePackets) {
			udpBuilding = new ArrayList<Packet>(windowSize);
			//udpDone = new LinkedList<DatagramPacket>();
		}
		//these are dependant on the starting value of ConnectionState.nextPacketId
		lastPidAcked = 0;
		front = 0;
		seenEOR = seenEOW = false;
	}

	public void setListener(SlidingWindowListener listener) {
		this.listener = listener;
	}
	
	/**
	 * Adds a packet to the window
	 * @param packet The packet to add
	 * @return true if not a duplicate and it appears the packet is in the right place
	 */
	public boolean addPacket(Packet packet) {
		int offsetFromFront = getOffsetFromFront(packet.getPacketID());
		
		if (offsetFromFront >= getWindowSize()) {
			return false;
		}
		
		int destIndex = (front + offsetFromFront) % packets.length;
		
		if (packets[destIndex].packet == null) {
			packets[destIndex].packet = packet;
			packets[destIndex].timeLastSent = packets[destIndex].timeAdded = System.currentTimeMillis();
			
			return true;
		} else {
			return false;
		}
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		for (int i=0; i<packets.length; i++) {
			sb.append("packets[" + i + "] = " + packets[i].packet + System.getProperty("line.separator"));
		}
		return sb.toString();
	}
	
	/**
	 * Oldest in packet order, not temporal
	 */
	public byte getLastPidAcked() {
		return lastPidAcked;
	}
	
	/**
	 * @return the packet id of the latest packet waiting to be used 
	 */
	public byte getLatestWaitingPid() {
		return (byte) ((getLastPidAcked() + getLatestWaitingOffset()));
	}
	
	/**
	 * returns a positive offset from the front, considering the wrapping effect
	 * @return
	 */
	public int getLatestWaitingOffset() {
		int offset = 0;
		int packetLoc = (front + offset) % packets.length;
		while (packets[packetLoc].packet != null) {
			offset++;
			packetLoc = (front + offset) % packets.length;

			//dont loop around completely (all packets filled)
			if (packetLoc == front) {
				return offset-1;
			}
		}
		return offset;
	}
	

	protected int getOffsetFromFront(byte pid) {
		//force it positive
		int ipid = pid & 0xff;
		int frontPid = (getLastPidAcked() + 1) & 0xff;
		
		if (ipid >= frontPid) {
			return ipid - frontPid;
		} else {
			return ((-frontPid)&0xff) + ipid;
		}
	}
	
	/**
	 * discards old packets and moves the front window pointer forward
	 * @param pid the packet to move to
	 * @return true if there were any changes
	 */
	public boolean moveFrontOfWindowToPid(byte pid) {
		int offset = getOffsetFromFront(pid);
		int ackIndex = (front + offset) % getWindowSize();
		
		//odd, this must be a late or duplicate packet
		if (offset >= getWindowSize()) {
			return false;
		}
		
		//System.out.println("moveFrontOfWindowToPid(" + pid + ")");
		//System.out.println(" front:" + front);
		//System.out.println(" lastPidAcked:" + lastPidAcked);
		//System.out.println(" offset:" + offset);
		
		if (packets[ackIndex].packet == null) {
			//System.out.println(" packets[" + ackIndex + "] is null");
			//this is probably a duplicate or late packet
			return false;
		}
		
		for (int i=0; i<=offset; i++) {
			int clearIndex = (front + i) % getWindowSize();
			Packet packetClearing = packets[clearIndex].packet; 
			assert (packetClearing != null);
			assert (packetClearing.getPacketID() == (byte)(lastPidAcked+1));
			
			if (udpBuilding != null) {
				udpBuilding.add(packetClearing);
			
				if (packetClearing.isAssemblyEnd()) {
					//DatagramPacket dgram = PacketConverter.convertToUDP(udpBuilding);
					
					//dont enable assembling if you arent going to listen
					assert(this.listener != null);
					if (this.listener != null) {
						listener.onSlidingWindowCollectedPackets(udpBuilding);
					}
					udpBuilding.clear();
				}
			}

			if (packetClearing.isEndOfRead()) {
				seenEOR = true;
			}
			
			if (packetClearing.isEndOfWrite()) {
				seenEOW = true;
			}

			//if we are clearing it then we've acked it
			lastPidAcked = packetClearing.getPacketID();
			packets[clearIndex].packet = null;
		}
		
		Log.get().println(Log.LEVEL_SPAM, "SlidingWindow front moving from " + front + " to " + (ackIndex+1));
		front = (ackIndex+1) % getWindowSize();
		return true;
	}
	
	public int getWindowSize() {
		return packets.length;
	}

	public Packet getPacket(byte pid) {
		int offset = getOffsetFromFront(pid);
		int index = (front + offset) % packets.length;
		
		if (offset >= getWindowSize()) {
			return null;
		}

		//this may be null too
		return packets[index].packet;
	}
	
	public boolean isFull() {
		int lastIndex = (front + getWindowSize() - 1) % getWindowSize();
		return packets[lastIndex].packet != null;
	}
	
	public Packet getOldestPacketIfLate(int millisecondsLate) {
		long currentTime = System.currentTimeMillis();
		PacketAndTime pkt = packets[front];
		if (currentTime - pkt.timeAdded > millisecondsLate) {
			pkt.timeLastSent = currentTime;
			return pkt.packet;
		}
		return null;
	}


	public Packet getLeastRecentlySentPacket() {
		PacketAndTime mostLate = null;
		for (int i=0; i<getWindowSize(); i++) {
			int index = (front + i) % getWindowSize();
			PacketAndTime pkt = packets[index];
			if (mostLate == null) {
				mostLate = pkt;
			} else {
				if (pkt.timeLastSent < mostLate.timeLastSent) {
					mostLate = pkt;
				}
			}
		}
		if (mostLate != null) {
			mostLate.timeLastSent = System.currentTimeMillis();
			return mostLate.packet;
		} else {
			return null;
		}
	}

	public Packet getLeastRecentlySentPacketIfLate(int millisecondsLate) {
		PacketAndTime mostLate = null;
		for (int i=0; i<getWindowSize(); i++) {
			int index = (front + i) % getWindowSize();
			PacketAndTime pkt = packets[index];
			if (mostLate == null) {
				mostLate = pkt;
			} else {
				if (pkt.timeLastSent < mostLate.timeLastSent) {
					mostLate = pkt;
				}
			}
		}
		if (mostLate != null) {
			long currentTime = System.currentTimeMillis();
			if (currentTime - mostLate.timeLastSent > millisecondsLate) {
				mostLate.timeLastSent = System.currentTimeMillis();
				return mostLate.packet;
			}
		}
		return null;
	}
	
	private class PacketAndTime {
		/** the packet */
		public Packet packet;
		/** time added */
		public long timeAdded;
		/** last time sent out */
		public long timeLastSent;
	}
	
	public boolean seenEndOfWrite() {
		return seenEOW;
	}
	
	public boolean seenEndOfRead() {
		return seenEOR;
	}

}

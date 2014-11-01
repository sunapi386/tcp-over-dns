package common;

import java.io.IOException;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.List;

public class ConnectionState implements SlidingWindowListener {
	public static final byte WINDOW_SIZE = 33;
	//this should probably be based on the client send interval, or be dynamic
	private int lateMillis;
	private short clientId;
	private SlidingWindowSet slidingWindows;
	private PacketConverter packetConverter;
	private PacketFactory packetFactory;
	/** A queue of mini packets to be sent to the dnsServer */
	private LinkedList<Packet> dnsQueue;
	private byte nextPacketId;
	private PortCommunicator portCommunicator;
	private int nextPacketIteration;
	private int backOff;
	private boolean throttle;
	private boolean hasSentConnectionClosed;
	
	public ConnectionState(short clientId, PacketConverter packetConverter, PacketFactory packetFactory, PortCommunicator portCommunicator, int lateMillis, boolean throttle) throws SocketException {
		this.clientId = clientId;
		this.slidingWindows = new SlidingWindowSet(WINDOW_SIZE);
		this.slidingWindows.setRecievedWindowListener(this);
		this.packetConverter = packetConverter;
		this.packetFactory = packetFactory;
		this.dnsQueue = new LinkedList<Packet>();
		this.nextPacketId = 1;
		this.lateMillis = lateMillis;
		this.portCommunicator = portCommunicator;
		this.throttle = throttle;
		this.hasSentConnectionClosed = false;
	}

	public short getClientId() {
		return clientId;
	}

	public void dnsPacketReceived(Packet p) {
		assert(p.isChecksumValid());

		slidingWindows.packetReceived(p);

		if (slidingWindows.receivedEndOfRead()) {
			portCommunicator.closeWrite();
		}
		
		if (slidingWindows.receivedEndOfWrite()) {
			portCommunicator.closeRead();
		}
		
		//we got an ack! reset our packetIteration if there are no more late packets
		Packet oldestLate = slidingWindows.getOldestPacketIfLate(lateMillis);
		if (oldestLate == null) {
			nextPacketIteration = 0;
		}
	}
	
	protected void dnsPacketSent(Packet p) {
		slidingWindows.packetSent(p);
	}
	
	protected Packet getNextPacket() {
		Packet pktToReturn = null;
				
		if (pktToReturn == null) {
			//the oldest packet is most important because acks must come in order,
			//every 2nd packet will be oldest
			if ((++nextPacketIteration%2) == 0) {
				pktToReturn = slidingWindows.getOldestPacketIfLate(lateMillis);
			}
		}
		
		/*
		if (pktToReturn == null) {
			//always priortize late packets because they hold up the entire pipeline
			pktToReturn = slidingWindows.getLeastRecentlySentPacketIfLate(lateMillis);
		}*/
		
		if (pktToReturn == null) {
			boolean canSendNewData = true;
			
			//if our send window is full, we cannot send new data packets
			if (!slidingWindows.canSendData()) {
				canSendNewData = false;
			}
			
			if (canSendNewData && dnsQueue.isEmpty()) { //if we ran out of packets to send, process another datagram
				canSendNewData = processOneTransmissionUnit();
			}
			
			if (canSendNewData) {
				pktToReturn = dnsQueue.removeFirst();
			} else { 
				//instead of sending a no-op resend any pending data
				pktToReturn = slidingWindows.getLeastRecentlySentPacket();
								
				//there is no pending data and both ends of the stream have closed, close the tunnel
				if (pktToReturn == null && slidingWindows.bothEndsClosed()) {
					pktToReturn = this.packetFactory.createConnectionClosed(clientId);
					hasSentConnectionClosed = true;
				}
				
				//finally if there really is nothing else to send, then send a no-op
				if (pktToReturn == null) {
					pktToReturn = packetFactory.createNoOp(clientId);
				}
			}
		}
		
		pktToReturn.setLastPidSeen(slidingWindows.getLatestWaitingPid());
		
		//here we reduce the number of duplicate packets sent to allow for other traffic
		if (throttle && pktToReturn.isNoOp() && slidingWindows.isOppositeEndIdle()) {
			backOff++;
			switch (backOff) {
			case 2:
			case 4:
			case 8:
				break;
			case 16:
				//count from 4 to 8 again
				backOff = 8;
				break;
			default:
				//the packet repeated too many times
				return null;
			}
		} else {
			backOff = 0;
		}
		return pktToReturn;		
	}
	
	public Packet getOldPacketSent(byte packetId) {
		return slidingWindows.getOldPacketSent(packetId);
	}

	private boolean processOneTransmissionUnit() {
		TransmissionUnit unit = portCommunicator.receiveIncoming();
		if (unit == null) {
			return false;
		}
	
		Packet [] packets = packetConverter.convertFromBytes(packetFactory, unit.data, unit.data.length, clientId, nextPacketId, unit.splittable, unit.endOfWrite, unit.endOfRead);
		nextPacketId = (byte)(packets.length + nextPacketId);
		
		for (Packet p : packets) {
			dnsQueue.add(p);
		}
		return true;
	}

	public Packet update() throws IOException {
		//outgoing udp packets are automatically assembled and sent with the 
		//  onSlidingWindowCollectedPackets callback.
		//incoming udp packets are automatically collected into datagramQueue
		//  with the onPacketReceived callback
		
		
		//see if we received any more packets locally
		//figure out what needs to go over DNS
		Packet outgoing = getNextPacket();
		if (outgoing == null) {
			return null;
		}
		//pretend the packet was already sent
		dnsPacketSent(outgoing);
		//return it (causes it to get sent)
		return outgoing;
	}
	
	public void onSlidingWindowCollectedPackets(List<Packet> packets) {
		byte [] data = packetConverter.convertToBytes(packets);
		if (data != null) {
			Log.get().println(Log.LEVEL_SPAM, "Assembled a packet and are sending it locally.");
			portCommunicator.sendPacket(data);
		} else {
			Log.get().println(Log.LEVEL_ERROR, "Packet assembly failed, packet dropped.");
		}
	}

	public void close() {
		portCommunicator.close();
	}

	public boolean hasTimedOut(int timeOut) {
		return slidingWindows.getAgeOfLastPacketReceived() > timeOut;
	}

	public boolean hasSentConnectionClosed() {
		return hasSentConnectionClosed;
	}
}

package common;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.util.Iterator;
import java.util.LinkedList;

public class UDPPortCommunicator extends PortCommunicator {

	public static final int TIMEOUT_TEST_INTERVAL = 1000;
	
	private DatagramSocket socket;
	private int mtu;
	private LinkedList<ForwardAddress> forwardAddrs;
	/** Clients must use dynamic addressing, the server is static addressing */
	private boolean dynamicAddressing;
	private long timeLastCheckedForTimeouts;
	private int dropTimer; 
	
	private LinkedList<TransmissionUnit> transmissionUnits;
	
	/**
	 * Construct a UDP port communicator
	 * @param socket The socket we read and write from
	 * @param mtu The maximum size, in bytes, of a transmission unit for receiving.
	 * @param dropTimer The amount of time to hang onto a packet.
	 * @param dynamicAddressing When true, new addresses will be added to our outgoing address list if we receieve from them.
	 */
	public UDPPortCommunicator(DatagramSocket socket, int mtu, int dropTimer, boolean dynamicAddressing) {
		super("UDP comm " + socket.getLocalSocketAddress());
		this.forwardAddrs = new LinkedList<ForwardAddress>();
		this.socket = socket;
		this.mtu = mtu;
		this.dynamicAddressing = dynamicAddressing;
		this.timeLastCheckedForTimeouts = System.currentTimeMillis();
		this.transmissionUnits = new LinkedList<TransmissionUnit>();
		this.dropTimer = dropTimer;
		assert(mtu > 0);
	}
	
	public boolean isClosed() {
		return socket.isClosed();
	}

	@Override
	public void run() {
		try {
			int currentMtu = mtu;
			DatagramPacket incoming = new DatagramPacket(new byte[currentMtu], currentMtu);
			while (!socket.isClosed()) {
				if (mtu != currentMtu) {
					currentMtu = mtu;
					incoming = new DatagramPacket(new byte[currentMtu], currentMtu);
				}
				
				incoming.setLength(mtu);
				socket.receive(incoming);
				Log.get().println(Log.LEVEL_SPAM, "Local network udp packet came in");
				
				
				byte [] dataCopy = new byte[incoming.getLength()];
				System.arraycopy(incoming.getData(), 0, dataCopy, 0, incoming.getLength());
				TransmissionUnit tu = new TransmissionUnit(dataCopy, false, false, false);
				
				dropLateUnits();
				
				synchronized (transmissionUnits) {
					transmissionUnits.add(tu);
				}
				
				firePacketReceived(/*incoming.getData(), incoming.getLength(), false*/);
				
				if (dynamicAddressing) {
					addPortForward(incoming.getSocketAddress());
				}
			}
		} catch (Exception e) {
			if (socket.isClosed()) {
				//its on purpose
			} else {
				Log.get().println(Log.LEVEL_ERROR, "UDP port communicator receiver errored.");
				Log.get().exception(Log.LEVEL_ERROR, e);
			}
		}
		
		socket.close();
		Log.get().println(Log.LEVEL_INFO, "Local UDP socket closed.");
	}

	@Override
	public void sendPacket(byte[] data) {
		if (isClosed()) {
			//TODO do something
			return;
		}

		synchronized (forwardAddrs) {
			DatagramPacket dgram = new DatagramPacket(data, 0, data.length);
			
			//forward it to all forwarding addresses
			Iterator<ForwardAddress> iter = forwardAddrs.iterator();
			while (iter.hasNext()) {
				ForwardAddress forwardAddr = iter.next();

				//send it along to an address				
				dgram.setSocketAddress(forwardAddr.sockAddr);
				try {
					socket.send(dgram);
				} catch (IOException e) {
					Log.get().println(Log.LEVEL_ERROR, "UDP port communicator sender errored.");
					Log.get().exception(Log.LEVEL_ERROR, e);

					//if we arent addressing dynamicly then this port is super important and we 
					//might as well keep trying. otherwise, chuck it
					if (dynamicAddressing) {
						iter.remove();
						Log.get().println(Log.LEVEL_INFO, "Removing udp forward address:" + forwardAddr.sockAddr);
					}
				}
			}
		}
	}
	
	/**
	 * Removes local forwarding addresses that havnt been heard from in a while
	 * @return true is there are no forwarding addresses remaining
	 */
	public boolean removeTimeouts() {
		long now = System.currentTimeMillis();
		synchronized (forwardAddrs) {
			//check for timeouts when we send packets
			if (dynamicAddressing) {
				Iterator<ForwardAddress> iter = forwardAddrs.iterator();
				while (iter.hasNext()) {
					ForwardAddress forwardAddr = iter.next();
				
					if (now - forwardAddr.timeLastPacketReceived > Common.UDP_TIMEOUT) {
						iter.remove();
						Log.get().println(Log.LEVEL_INFO, "Removing udp forward address:" + forwardAddr.sockAddr);
						continue;
					}
				}
			}
			return forwardAddrs.isEmpty();
		}
	}
	
	public void addPortForward(SocketAddress socketAddress) {
		long now = System.currentTimeMillis();
		synchronized (forwardAddrs) {
			
			Iterator<ForwardAddress> iter = forwardAddrs.iterator();
			boolean found = false;
			while (iter.hasNext()) {
				ForwardAddress addr = iter.next();
				if (addr.sockAddr.equals(socketAddress)) {
					addr.timeLastPacketReceived = now;
					found = true;
				}
			}
			
			if (!found) {
				Log.get().println(Log.LEVEL_INFO, "Adding udp forward address:" + socketAddress);
				forwardAddrs.add(new ForwardAddress(socketAddress, System.currentTimeMillis()));
			}
		}
	}

	public int getMtu() {
		return mtu;
	}

	public void setMtu(int mtu) {
		this.mtu = mtu;
	}

	@Override
	public void close() {
		//dynamic addres can not be closed, it must remain open to listen for new connections
		if (!dynamicAddressing) {
			socket.close();
			Log.get().println(Log.LEVEL_INFO, "Local UDP socket closed.");
		}
	}

	public boolean incomingExhausted() {
		//theres still data left to send
		synchronized (transmissionUnits) {
			if (!transmissionUnits.isEmpty()) {
				return false;
			}
		}
			
		if (isClosed()) {
			return true;
		}
		
		long now = System.currentTimeMillis(); 
		if (dynamicAddressing) {
			if (now - timeLastCheckedForTimeouts > TIMEOUT_TEST_INTERVAL) {
				boolean fwdsEmpty = removeTimeouts();
				timeLastCheckedForTimeouts = now;
				return fwdsEmpty;
			} else {
				synchronized (forwardAddrs) {
					return forwardAddrs.isEmpty();
				}
			}
		}
		return false;
	}
	
	private class ForwardAddress {
		public SocketAddress sockAddr;
		public long timeLastPacketReceived;
		
		public ForwardAddress(SocketAddress sockAddr, long timeLastPacketReceived) {
			this.sockAddr = sockAddr;
			this.timeLastPacketReceived = timeLastPacketReceived;
		}
			
	}

	@Override
	public TransmissionUnit receiveIncoming() {
		synchronized (transmissionUnits) {
			if (transmissionUnits.isEmpty()) {
				if (incomingExhausted()) {
					return new TransmissionUnit(new byte [0], false, true, true); 
				}
				return null;
			} else {
				return transmissionUnits.removeFirst();
			}
		}
	}

	public void dropAccumulatedUnits() {
		synchronized (transmissionUnits) {
			transmissionUnits.clear();	
		}
	}

	public void dropLateUnits() {
		synchronized (transmissionUnits) {
			long now = System.currentTimeMillis();
			//we can drop old UDP packets
			while (!transmissionUnits.isEmpty()) {
				TransmissionUnit old = transmissionUnits.getFirst();
				
				if (old.time + dropTimer > now) {
					transmissionUnits.removeFirst();
				} else {
					break;
				}
			}
		}
	}

	@Override
	public void closeRead() {
	}

	@Override
	public void closeWrite() {
	}

	
}

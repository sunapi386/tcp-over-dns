package common;

import java.util.ArrayList;

public abstract class PortCommunicator extends Thread {

	private ArrayList<PortCommunicatorListener> listeners = new ArrayList<PortCommunicatorListener>();
	
	public PortCommunicator() {
		super();
	}
	
	public PortCommunicator(String name) {
		super(name);
	}
	
	public synchronized void addListener(PortCommunicatorListener listener) {
		listeners.add(listener);
	}
	public synchronized boolean removeListener(PortCommunicatorListener listener) {
		return listeners.remove(listener);
	}
	
	/** 
	 * The communicator received a packet.
	 */
	protected synchronized void firePacketReceived() {
		for (PortCommunicatorListener l : listeners) {
			l.onPacketReceived();
		}
	}

	public abstract TransmissionUnit receiveIncoming();
	//public abstract boolean isDefunct();
	public abstract void sendPacket(byte [] data);
	//public abstract boolean incomingExhausted();
	public abstract void close();
	public abstract void closeWrite();
	public abstract void closeRead();
}

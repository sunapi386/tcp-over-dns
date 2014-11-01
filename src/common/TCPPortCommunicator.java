package common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class TCPPortCommunicator extends PortCommunicator {

	private Socket socket;
	private TransmissionUnit transWaiting;
	private boolean doneReading;
	private boolean doneWriting;
	
	private InputStream sockIn;
	private OutputStream sockOut;
	
	private static final int WRITE_CLOSED_POLL_INTERVAL = 1200;
	
	private Thread writeClosedChecker = new Thread() {
		@Override
		public void run() {
			while (!socket.isOutputShutdown()) {
				try {
					sleep(WRITE_CLOSED_POLL_INTERVAL);
				} catch (InterruptedException e) {
				}
			}
			closeWrite();
		}
	};

	public TCPPortCommunicator(Socket socket) {
		super("TCP comm " + socket.getLocalSocketAddress());
		this.socket = socket;
		this.doneReading = false;
		this.doneWriting = false;
		
		try {
			sockIn = socket.getInputStream();
			sockOut = socket.getOutputStream();
		} catch (IOException e) {
			//e.printStackTrace();
		}
		
		writeClosedChecker.start();
	}
	
	@Override
	public void run() {
		byte [] data = new byte [Common.DEFAULT_MTU];
		int dataLen;
		try {
			
			while (!doneReading) {
				dataLen = sockIn.read(data, 0, data.length);
				if (this.isInterrupted()) {
					break;
				}
				//-1 means EOF
				if (dataLen == -1) {
					break;
				}
				
				Log.get().println(Log.LEVEL_SPAM, "Local network TCP data came in.");
				
				byte [] dataCopy = new byte[dataLen];
				System.arraycopy(data, 0, dataCopy, 0, dataLen);
				TransmissionUnit newUnit = new TransmissionUnit(dataCopy, true, doneWriting, doneReading);
				
				synchronized (this) {
					transWaiting = newUnit;
					this.wait();
				}
				
				firePacketReceived();
			}
		} catch (Exception e) {
		}
		closeRead();
	}

	@Override
	public synchronized void sendPacket(byte[] data) {
		if (data.length == 0) {
			//TODO do something
			return;
		}
		
		try {
			sockOut.write(data, 0, data.length);
		} catch (IOException e) {
			closeWrite();
		}
	}
	
	@Override
	public synchronized TransmissionUnit receiveIncoming() {
		TransmissionUnit tu = transWaiting;
		transWaiting = null;
		
		if (tu != null) {
			this.notifyAll();
		}
		
		return tu;
	}

	
	@Override
	public synchronized void close() {
		try {
			doneReading = true;
			doneWriting = true;
			socket.close();
			Log.get().println(Log.LEVEL_INFO, "Local TCP socket closed.");
			this.notifyAll();
		} catch (IOException e) {
		}
	}
	
	@Override
	public synchronized void closeRead() {
		if (doneReading) {
			return;
		}
		
		doneReading = true;
		try {
			this.sockIn.close();
			Log.get().println(Log.LEVEL_INFO, "Local TCP incoming half socket closed.");
		} catch (IOException e) {
		}
		
		updateTransWaiting();
		
		this.notifyAll();
	}

	@Override
	public synchronized void closeWrite() {
		if (doneWriting) {
			return;
		}
		
		doneWriting = true;
		try {
			this.sockOut.close();
			Log.get().println(Log.LEVEL_INFO, "Local TCP outgoing half socket closed.");
		} catch (IOException e) {
		}
		
		updateTransWaiting();
	}
	
	private synchronized void updateTransWaiting() {
		if (transWaiting == null) {
			transWaiting = new TransmissionUnit(new byte [0], false, doneWriting, doneReading);
			firePacketReceived();
		} else {
			transWaiting.endOfWrite = doneWriting;
			transWaiting.endOfRead = doneReading;
		}
	}

}

package client;

import java.io.IOException;
import java.net.Socket;
import java.util.Random;

import common.ConnectionRequestClientPacket;

public class PendingConnection {
	private ConnectionRequestClientPacket request;
	private long timeLastAsked;
	private Socket socket;
	private int retries;
	
	public PendingConnection(ConnectionRequestClientPacket request, Socket socket) {
		this.request = request;
		this.timeLastAsked = 0;
		this.socket = socket;
		this.retries = 0;
	}

	public boolean hasBeenRequested(int reaskTimeout) {
		return (System.currentTimeMillis() - timeLastAsked) > reaskTimeout;
	}

	public ConnectionRequestClientPacket getRequestForSending() {
		request.setChallengeId(new Random().nextInt());
		timeLastAsked = System.currentTimeMillis();
		return request;
	}

	public long getTimeLastAsked() {
		return timeLastAsked;
	}

	public boolean isTCP() {
		return (request.getRequestFlags() & ConnectionRequestClientPacket.FLAG_TCP) != 0;
	}

	public Socket getSocket() {
		return socket;
	}

	public int getChallengeId() {
		return request.getChallengeId();
	}
	
	public void cancel() {
		try {
			socket.close();
		} catch (IOException e) {
		}
	}

	/**
	 * increments retry count and returns current value
	 * @return number of times connection has been retried
	 */
	public int increaseRetries() {
		//abort early if the socket was closed
		if (socket.isClosed()) {
			return 999;
		}
		return ++retries;
	}
}

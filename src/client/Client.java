package client;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Random;

import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Opcode;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.Type;

import common.ClientPacket;
import common.Common;
import common.ConnectionAcceptedServerPacket;
import common.ConnectionRequestClientPacket;
import common.ConnectionState;
import common.DNSSizes;
import common.LZMAPacketConverter;
import common.Log;
import common.Packet;
import common.PortCommunicator;
import common.PortCommunicatorListener;
import common.ServerPacket;
import common.SpecialServerPacket;
import common.TCPPortCommunicator;
import common.UDPPortCommunicator;

public class Client implements PortCommunicatorListener {
	public static final String CLIENT_NAME = "tcp-over-dns-client";
	
	public static final int DEFAULT_PORT     = 8080;
	public static final String DEFAULT_ADDR  = "0.0.0.0";
	public static final int DEFAULT_INTERVAL = 200;
	public static final int DEFAULT_TRUNCATION_ALLOWANCE = 0;
	public static final int REASK_TIMEOUT    = 3000;

	public static final int MAX_CONNECTION_RETRIES = 20;
	
	private int dnsPacketId;
	private ClientOptions clientOpts;
	private final LinkedList<ServerPacket> dnsMessages;
	private final LinkedList<PendingConnection> pendingConnections;
	private ArrayList<ConnectionState> connections;
	private long lastPacketSent;
	private ArrayList<DNSServer> dnsSocks;
	private int dnsIndex;
	private int lastConnectionUsed;
	private ArrayList<DNSListenThread> dnsThreads;
	
	private UDPPortCommunicator udpCommunicator;
	
	public static void main(String [] args) {
		try {
			ClientCmdLineParser clp = new ClientCmdLineParser();
			ClientOptions opts = clp.parseCmdLine(args);
			if (opts == null) {
				clp.printUsage();
			} else {
				Log.set(new Log(opts.logFile, Log.integerToLevels(opts.logLevel)));
				new Client(opts).loop();
			}
		} catch (IOException e) {
			System.err.println(e);
			System.exit(-1);
		}
	}

	public static void logOptions(ClientOptions opts) {
		Log.get().println(Log.LEVEL_INFO, CLIENT_NAME + " starting up");
		Log.get().println(Log.LEVEL_INFO, "Connecting to domain: " + opts.domain);
		for (InetAddress dns : opts.dnsServers) {
			Log.get().println(Log.LEVEL_INFO, "Using dns server: " + Common.DEFAULT_DNS_PORT + ":" + dns);
		}
		Log.get().println(Log.LEVEL_INFO, "Interval: " + opts.interval);
		Log.get().println(Log.LEVEL_INFO, "MTU: " + opts.mtu);
		Log.get().println(Log.LEVEL_INFO, "Listening on: " + opts.listenPort + ":" + opts.listenAddr);
		Log.get().println(Log.LEVEL_INFO, "Truncation allowance: " + opts.truncationAllowance);
		Log.get().println(Log.LEVEL_INFO, "Query type: " + (opts.queryType == ClientOptions.TXT_QUERIES ? "TXT" : "CNAME"));
		Log.get().println(Log.LEVEL_INFO, "Client encoding: " + ClientCmdLineParser.encodingToString(opts.clientEnc));
		Log.get().println(Log.LEVEL_INFO, "Log level: " + opts.logLevel);
	}
	
	public Client(ClientOptions opts) throws IOException {
		logOptions(opts);
		
		this.connections = new ArrayList<ConnectionState>();
		this.clientOpts = opts;
		this.dnsSocks = new ArrayList<DNSServer>();
		this.pendingConnections = new LinkedList<PendingConnection>();
		this.dnsThreads = new ArrayList<DNSListenThread>();
		
		for (InetAddress iaddr : opts.dnsServers) {
			InetSocketAddress dnsTarget = new InetSocketAddress(iaddr, Common.DEFAULT_DNS_PORT);
			DatagramSocket dnsSock = new DatagramSocket(0, opts.dnsRequestAddress);
			DNSServer sockAndTarget = new DNSServer(dnsSock, dnsTarget);
			dnsSocks.add(sockAndTarget);
					
			DNSListenThread dnsThread = new DNSListenThread(sockAndTarget);
			dnsThread.start();
			dnsThreads.add(dnsThread);
		}
		this.dnsMessages = new LinkedList<ServerPacket>();

		this.udpCommunicator = new UDPPortCommunicator(new DatagramSocket(clientOpts.listenPort, clientOpts.listenAddr), Common.DEFAULT_MTU, getLateTimerMillis(clientOpts.interval)*2, true) {
			//when the udp communicator closes, dont actually close the socket
			@Override
			public void close() {
				//if we get here it means the server closed our one and only UDP connection.
				
				//dont close the socket, dont stop the thread
				//super.close();
				
				//wait for more communication on this port and reconnect if we get some
				udpCommunicator = this;
				udpCommunicator.addListener(Client.this);
				udpCommunicator.dropAccumulatedUnits();
			}
		};
		this.udpCommunicator.addListener(this);
		this.udpCommunicator.start();
		
		Thread tcpListener = new Thread("TCP acceptor") {
			public void run() {
				listenToTCP(clientOpts.listenAddr, clientOpts.listenPort);
			}
		};
		tcpListener.start();
	}
	
	private void listenToTCP(InetAddress addr, int port) {
		try {
			ServerSocket serverSocket = new ServerSocket(port, 8, addr);
			while (true) {
				Socket socket = serverSocket.accept();
				socket.setTcpNoDelay(true);
				socket.setSendBufferSize(Common.DEFAULT_MTU);
				
				ConnectionRequestClientPacket request = 
					new ConnectionRequestClientPacket(
							0, new Random().nextInt(), 
							getLateTimerMillis(clientOpts.interval), 
							ConnectionRequestClientPacket.FLAG_TCP,
							(short)clientOpts.truncationAllowance);
				
				PendingConnection pendConn = new PendingConnection(request, socket);
				
				synchronized (pendingConnections) {
					pendingConnections.add(pendConn);
				}
			}
		} catch (IOException e) {
			Log.get().println(Log.LEVEL_ERROR, "TCP listening socket failed!");
			Log.get().exception(Log.LEVEL_ERROR, e);
		}
	}
	
	//the first UDP packet to come in will trigger a connection to the server
	public void onPacketReceived(/*byte[] data, int dataLen, boolean splittable*/) {
 		int pendingChallengeId = new Random().nextInt();
		ConnectionRequestClientPacket conReq = new ConnectionRequestClientPacket(
				clientOpts.mtu,
				pendingChallengeId,
				getLateTimerMillis(clientOpts.interval), 
				0,
				(short)clientOpts.truncationAllowance);
		
		synchronized (pendingConnections) {
			for (PendingConnection pc : pendingConnections) {
				//if there is already a pending UDP connection then do nothing
				if (!pc.isTCP()) {
					return;
				}
			}
			pendingConnections.add(new PendingConnection(conReq, null));
		}
	}

	
	public synchronized void loop() {
		lastPacketSent = System.currentTimeMillis() - 2*clientOpts.interval;
		while (true) {
			int timeout = clientOpts.interval;
			try {
				timeout = update();
			} catch (IOException e1) {
				Log.get().exception(Log.LEVEL_ERROR, e1);
			}
			
			if (timeout > 0) {
				try {
					//the only thing that should wake us up is new packets coming in
					synchronized (dnsMessages) {
						dnsMessages.wait(timeout);
					}
				} catch (InterruptedException e) {		
				}
			}
		}
	}
	
	/**
	 * 
	 * @return Timeout to wait until calling update again
	 * @throws IOException
	 */
	private int update() throws IOException {
		
		handOutCollectedMessages();
		
		//now see if we should go ahead and send out the next packet
		long currentTime = System.currentTimeMillis();
		long timeToWait = lastPacketSent + clientOpts.interval - currentTime; 
		if (timeToWait <= 0) {
			ClientPacket clientPkt = getNextPendingConnectionPacket();

			if (clientPkt == null) {
				clientPkt = getNextDataPacket();
				
				if (clientPkt == null) {
					return clientOpts.interval;
				}
			}
			
			//simulate some packet loss
			//if (new Random().nextDouble() > .7) {
			//	return clientOpts.interval;
			//}
			
			clientPkt.updateChecksum();
			Log.get().println(Log.LEVEL_SPAM, "Sent: " + clientPkt);
			
			Message dnsQuery = new Message((dnsPacketId++) & 0xffff);
			dnsQuery.getHeader().setOpcode(Opcode.QUERY);
			dnsQuery.getHeader().setFlag(Flags.RD);
	
			Name encodedName = clientPkt.encodeAsName(clientOpts.domain);

			Record question;
			if (clientOpts.queryType == ClientOptions.TXT_QUERIES) {
				//do not use Inet4Address.getLocalHost (breaks some systems)
				question = new TXTRecord(encodedName, DClass.IN, Common.DEFAULT_TTL, "");//Inet4Address.getByAddress(new byte[] {127,0,0,1}));
			} else {
				question = new CNAMERecord(encodedName, DClass.IN, Common.DEFAULT_TTL, Name.fromString("dummy.com."));//Inet4Address.getByAddress(new byte[]{0,0,0,0}));
			}
			dnsQuery.addRecord(question, Section.QUESTION);
			
			byte [] query = dnsQuery.toWire();
			sendToDNS(query);
			
			lastPacketSent = currentTime;
		}
		
		timeoutConnections();
		
		//just in case we get a really crazy underflow somehow
		int maxTimeToWait = Math.min((int)timeToWait, clientOpts.interval);
		maxTimeToWait = Math.max(0, maxTimeToWait);
		return (int)maxTimeToWait;
	}

	private ClientPacket getNextDataPacket() throws IOException {
		ClientPacket clientPkt = null;
		//if there were no pending connections then send some data
		for (int i=0; i<this.connections.size(); i++) {
			//iterate through the connections
			lastConnectionUsed = (lastConnectionUsed + 1) % this.connections.size();
			ConnectionState connectionState = connections.get(lastConnectionUsed);
			//the state can return null if the packet would have been a repeat
			clientPkt = (ClientPacket) connectionState.update();
			if (clientPkt != null) {
				break;
			}
		}
		return clientPkt;
	}

	private ClientPacket getNextPendingConnectionPacket() {
		ClientPacket clientPkt = null;
		synchronized (pendingConnections) {
			Iterator<PendingConnection> iter = pendingConnections.iterator();
			while (iter.hasNext()) {
				PendingConnection pending = iter.next();
				
				if (pending.hasBeenRequested(REASK_TIMEOUT)) {
					if (pending.increaseRetries() == MAX_CONNECTION_RETRIES) {
						Log.get().println(Log.LEVEL_INFO, "Connection request cancelled.");
						pending.cancel();
						iter.remove();
					} else {
						clientPkt = pending.getRequestForSending();
						break;
					}
				}
			}
		}
		return clientPkt;
	}

	/**
	 * Gives the messages to the connection streams
	 * @throws SocketException
	 */
	private void handOutCollectedMessages() throws SocketException {
		synchronized (dnsMessages) {
			while (!dnsMessages.isEmpty()) {
				ServerPacket pktReceived = (ServerPacket) dnsMessages.removeFirst();
				if (!pktReceived.isChecksumValid()) {
					Log.get().println(Log.LEVEL_SPAM, "Packet had bad checksum.");
					continue;
				}
				
				Log.get().println(Log.LEVEL_SPAM, "Received: " + pktReceived.toString());
				
				if (pktReceived.isSpecial()) {
					handleSpecialPacket((SpecialServerPacket)pktReceived);
				}
				for (ConnectionState conn : connections) {
					if (conn.getClientId() == pktReceived.getClientID()) {
						conn.dnsPacketReceived(pktReceived);
					}
				}
			}
		}
	}

	private void timeoutConnections() {
		for (int i=connections.size()-1; i>=0; i--) {
			ConnectionState client = connections.get(i);
			//client gives up 1 second before server
			if (client.hasTimedOut(Common.CONN_TIMEOUT - 1000)) {
				if (client.hasSentConnectionClosed()) {
					Log.get().println(Log.LEVEL_INFO, "Server connection closed (Client ID:" + (0xFFFF & client.getClientId()) + ").");
				} else {
					Log.get().println(Log.LEVEL_INFO, "Server timeout (Client ID:" + (0xFFFF & client.getClientId()) + ").");
				}
				client.close();
				connections.remove(client);
			}
		}
	}

	private void sendToDNS(byte[] query) throws SocketException, IOException {
		DNSServer dns = dnsSocks.get(dnsIndex);
		dns.getSocket().send(new DatagramPacket(query, query.length, dns.getTarget()));
		dnsIndex = (dnsIndex + 1) % dnsSocks.size();
	}

	private void handleSpecialPacket(SpecialServerPacket pktReceived) throws SocketException {
		switch (pktReceived.getType()) {
		case SpecialServerPacket.SPECIAL_TYPE_CONNECTION_ACCEPTED:
			ConnectionAcceptedServerPacket conAcc = (ConnectionAcceptedServerPacket) pktReceived;
			PendingConnection requestAccepted = null;
			
			synchronized (pendingConnections) {
				Iterator<PendingConnection> pendingIter = pendingConnections.iterator();
				while (pendingIter.hasNext()) {
					PendingConnection pc = pendingIter.next();
					if (pc.getChallengeId() == conAcc.getChallengeId()) {
						pendingIter.remove();
						requestAccepted = pc;
						break;
					}
				}
			}
			if (requestAccepted == null) {
				return;
			}

			PortCommunicator comm;
			ConnectionState connectionState;
			
			if (requestAccepted.isTCP()) {
				comm = new TCPPortCommunicator(requestAccepted.getSocket());

				connectionState = new ConnectionState(
						conAcc.getClientID(), 
						new LZMAPacketConverter(),
						new ClientPacketFactory(clientOpts.domain, clientOpts.clientEnc),
						comm,
						getLateTimerMillis(clientOpts.interval),
						true);

				//critical that this starts AFTER the connectionState is constructed
				comm.start();
				Log.get().println(Log.LEVEL_INFO, "TCP Connection established (clientID:" + (conAcc.getClientID()&0xFFFF) + ")");
			} else {
				udpCommunicator.setMtu(conAcc.getMaxMTU());
				comm = udpCommunicator;
				//this comm is already started
				udpCommunicator.removeListener(this);
				udpCommunicator = null;

				
				connectionState = new ConnectionState(
						conAcc.getClientID(), 
						new LZMAPacketConverter(),
						new ClientPacketFactory(clientOpts.domain, Packet.ENC_FULL63),
						comm,
						getLateTimerMillis(clientOpts.interval),
						true);

				
				Log.get().println(Log.LEVEL_INFO, "UDP Connection established (clientID:" + (conAcc.getClientID()&0xFFFF) + " mtu: " + conAcc.getMaxMTU() + ")");
			}
				
			
			connections.add(connectionState);
			break;
		case SpecialServerPacket.SPECIAL_TYPE_CONNECTION_CLOSED:
			for (int i=0; i<connections.size(); i++) {
				ConnectionState connection = connections.get(i);
				if (connection.getClientId() == pktReceived.getClientID()) {
					connection.close();
					connections.remove(i);
					break;
				}
			}
			break;
		}
	}

/*	protected class LocalListenThread extends Thread {
		DatagramSocket listenSock;
		
		public LocalListenThread() throws SocketException {
			this.listenSock = new DatagramSocket(clientOpts.listenPort);
		}

		@Override
		public void run() {
			while (true) {
				DatagramPacket packet = new DatagramPacket(new byte[clientOpts.mtu], clientOpts.mtu);
				try {
					listenSock.receive(packet);
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
				synchronized (Client.this) {
					listenerPackets.add(packet);
					Client.this.notifyAll();
				}
			}
		}	
	}
*/	
	protected class DNSListenThread extends Thread {
		private DNSServer dns;
		
		public DNSListenThread(DNSServer dns) throws SocketException {
			super("DNS Listener");
			this.dns = dns;
		}

		@Override
		public void run() {
			while (true) {
				DatagramPacket packet = new DatagramPacket(new byte[DNSSizes.MAX_PACKET_SIZE], DNSSizes.MAX_PACKET_SIZE);
				try {
					dns.getSocket().receive(packet);
					Message query = new Message(packet.getData());
				
					//System.out.println(query);
					
					

					if (!query.getHeader().getFlag(Flags.QR)) {
						Log.get().println(Log.LEVEL_WARN, "Received a dns query! This is backwards, the client should be getting responses.");
						continue;
					}
					
					if (query.getRcode() != Rcode.NOERROR) {
						Log.get().println(Log.LEVEL_ERROR, "Received error code from DNS server! " + Rcode.string(query.getRcode()));
						if (Rcode.NXDOMAIN == query.getRcode()) {
							Log.get().println(Log.LEVEL_ERROR, "Non-existant domain, it is likely your name servers are configured improperly.");
						}
						continue;
					}
					
					if (query.getHeader().getFlag(Flags.TC)) {
						Log.get().println(Log.LEVEL_WARN, "Received dns response with truncated flag!");
						//this should not happen, re init the dns socket
						//dns.reinitSocket();
						//continue;
					}
					
					Record [] records = query.getSectionArray(Section.ANSWER);
					if (records == null || records.length == 0) {
						Log.get().println(Log.LEVEL_ERROR, "Received dns response with no answer! Is the server running?");
						continue;
					}
					
					if (clientOpts.queryType == ClientOptions.TXT_QUERIES) {
						if (records[0].getType() != Type.TXT) {
							Log.get().println(Log.LEVEL_ERROR, "Answer not TXT!");
							continue;
						}
					} else {
						if (records[0].getType() != Type.CNAME) {
							Log.get().println(Log.LEVEL_ERROR, "Answer not CNAME!");
							continue;
						}
						
					}
					
					ServerPacket srvPkt = ServerPacket.decodeServerPacket(records[0]);
				 	
					synchronized (dnsMessages) {
						dnsMessages.add(srvPkt);
						dnsMessages.notifyAll();
					}
				} catch (IOException ioe) {
					Log.get().exception(Log.LEVEL_ERROR, ioe);
				}
			}
		}	
	}
	
	public static int getLateTimerMillis(int interval) {
		return interval * (ConnectionState.WINDOW_SIZE/2);
	}
	
	public class DNSServer {
		private InetSocketAddress target;
		private DatagramSocket sock;
		
		public DNSServer() {
		}
		
		public DNSServer(DatagramSocket sock, InetSocketAddress target) {
			this.sock = sock;
			this.target = target;
		}
		
		public synchronized InetSocketAddress getTarget() {
			return this.target;
		}
		
		public DatagramSocket getSocket() {
			return this.sock;
		}
		
		public synchronized void reinitSocket() throws SocketException {
			this.sock = new DatagramSocket(0, clientOpts.dnsRequestAddress);
		}
	}

}

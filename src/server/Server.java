package server;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintStream;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Flags;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.OPTRecord;
import org.xbill.DNS.Opcode;
import org.xbill.DNS.Rcode;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import common.ClientPacket;
import common.Common;
import common.ConnectionAcceptedServerPacket;
import common.ConnectionClosedClientPacket;
import common.ConnectionClosedServerPacket;
import common.ConnectionRequestClientPacket;
import common.ConnectionState;
import common.LZMAPacketConverter;
import common.Log;
import common.Packet;
import common.PortCommunicator;
import common.ServerPacket;
import common.SpecialClientPacket;
import common.TCPPortCommunicator;
import common.UDPPortCommunicator;

public class Server {
	public static final String SERVER_NAME = "tcp-over-dns-server";
	public static final String DEFAULT_ADDR = "0.0.0.0";

	private HashMap<Short, ConnectionState> clients = new HashMap<Short, ConnectionState>();
	private InetSocketAddress forwardAddress;
	private ServerOptions serverOpts;
	private Random rand;
	
	public static void main(String[] args) {
		try {
			ServerCmdLineParser clp = new ServerCmdLineParser();
			ServerOptions opts = null;
			try {
				opts = clp.parseCmdLine(args);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			if (opts == null) {
				clp.printUsage();
			} else {
				//if we are using an inherited channel and no logfile, disable
				//logging because stdout may conflict with the channel
				boolean disableLogging = opts.inheritedChannel != null && opts.logFile == null;
				if (disableLogging) {
					Log.set(new Log((PrintStream)null, 0));
				} else {
					Log.set(new Log(opts.logFile, Log.integerToLevels(opts.logLevel)));
				}
				new Server(opts);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public Server(ServerOptions opts) throws IOException {
		serverOpts = opts;
		logOptions();
		
		rand = new Random();
		
		forwardAddress = new InetSocketAddress(serverOpts.forwardAddr, serverOpts.forwardPort);
		
		//are we inherited a dgram channel?
		if (serverOpts.inheritedChannel != null) {
			// Serve DNS over it
			new Thread("DNS Serve Inherited Channel") {
				public void run() {
					serveDNS(serverOpts.inheritedChannel);
				}
			}.start();
		} else {
			//only attempt to open the ports if we arent using an 
			//inherited channel
			for (InetAddress addr : serverOpts.listenAddrs) {
				for (int port : serverOpts.listenPorts) {
					addUDP(addr, port);
				}
			}				
		}
		
		ClientTimeoutThread timeoutThread = new ClientTimeoutThread();
		timeoutThread.start();
	}

	public void logOptions() {
		Log.get().println(Log.LEVEL_INFO, SERVER_NAME + " starting up");
		Log.get().println(Log.LEVEL_INFO, new Date());
		Log.get().println(Log.LEVEL_INFO, "Hosting domain: " + serverOpts.domain);
		for (InetAddress dns : serverOpts.listenAddrs) {
			for (Integer port : serverOpts.listenPorts) {
				Log.get().println(Log.LEVEL_INFO, "DNS listening on: " + dns + ":" + port);
			}
		}
		Log.get().println(Log.LEVEL_INFO, "Forwarding to: " + serverOpts.forwardAddr + ":" + serverOpts.forwardPort);
		Log.get().println(Log.LEVEL_INFO, "MTU: " + serverOpts.mtu + " bytes");
		Log.get().println(Log.LEVEL_INFO, "Log level: " + serverOpts.logLevel);
		if (serverOpts.idleTimeout > 0) {
			Log.get().println(Log.LEVEL_INFO, "Server idle timeout: "+ serverOpts.idleTimeout + " seconds");
		} else {
			Log.get().println(Log.LEVEL_INFO, "Server idle timeout disabled");
		}
	}
	
	/*
	 * Note: a null return value means that the caller doesn't need to do
	 * anything.
	 */
	byte[] generateReply(Message query, Socket s)
			throws IOException {
		
		//pretend 30% packet loss
		//if (new Random().nextFloat() > .7) {
		//	return null;
		//}
		
		Header header = query.getHeader();
		
		//if they are sending us a query response then the response packets 
		//are going in the wrong direction
		if (header.getFlag(Flags.QR))
			return null;
		//if there was already an error, just report it
		if (header.getRcode() != Rcode.NOERROR)
			return errorMessage(query, Rcode.FORMERR);
		//we can only handle queries at this point
		if (header.getOpcode() != Opcode.QUERY)
			return errorMessage(query, Rcode.NOTIMP);

		//every response has the question in it
		Record queryRecord = query.getQuestion();

		int type = queryRecord.getType();
		//general case
		if (!Type.isRR(type) && type != Type.ANY) {
			return errorMessage(query, Rcode.NOTIMP);
		}

		//right now we only support CNAME and TXT
		if (type != Type.TXT && type != Type.CNAME) {
			return errorMessage(query, Rcode.NOTIMP);
		}
		
		boolean isTXT = type == Type.TXT;
		
		Message response = new Message(query.getHeader().getID());
		response.getHeader().setFlag(Flags.QR);
		response.getHeader().setFlag(Flags.RD);
		response.getHeader().setFlag(Flags.RA);
		response.getHeader().setFlag(Flags.AA);
		response.addRecord(queryRecord, Section.QUESTION);
		
		Packet clientPkt;
		Packet responsePkt;
		try {
			
			clientPkt = ClientPacket.decodeClientPacket(queryRecord.getName(), serverOpts.domain);
			Log.get().println(Log.LEVEL_SPAM, "Received: " + clientPkt);

			if (!clientPkt.isChecksumValid()) {
				throw new ProtocolException("Bad Checksum");
			}
			
			//special protocol message
			if (clientPkt.isSpecial()) {
				try {
					responsePkt = handleSpecialPacket((SpecialClientPacket) clientPkt, isTXT);
				} catch (Exception e) {
					Log.get().exception(Log.LEVEL_ERROR, e);
					return errorMessage(query, Rcode.FORMERR);
				}
			} else {//general data message
				ConnectionState client = getClient(clientPkt.getClientID());
				if (client == null) {
					Log.get().println(Log.LEVEL_WARN, "Message from unknown client! ClientID: " + (0xffff & clientPkt.getClientID()));
					responsePkt = new ConnectionClosedServerPacket(clientPkt.getClientID());
				} else {
					//its possible that 2 messages could come in on different UDP serving threads at the same time
					synchronized (client) {
						client.dnsPacketReceived(clientPkt);
						responsePkt = (ServerPacket) client.update();
					}
				}
			}
		} catch (Exception e) {
			Log.get().exception(Log.LEVEL_ERROR, e);
			return errorMessage(query, Rcode.FORMERR);
		}

		//byte [] randAddr = new byte[4];
		//rand.nextBytes(randAddr);
		responsePkt.updateChecksum();
		
		Record responseRecord;
		if (isTXT) {
			responseRecord = responsePkt.encodeAsTxt(queryRecord.getName(), DClass.IN, Common.DEFAULT_TTL); 
		} else {
			responseRecord = responsePkt.encodeAsCname(queryRecord.getName(), DClass.IN, Common.DEFAULT_TTL);
		}
		assert (null != ServerPacket.decodeServerPacket(responseRecord));

		
		response.addRecord(responseRecord, Section.ANSWER);
		
		Log.get().println(Log.LEVEL_SPAM, "Sent: " + responsePkt);

		return response.toWire(determineMaxResponseLength(s, query.getOPT()));
	}

	private ServerPacket handleSpecialPacket(SpecialClientPacket clientPkt, boolean isTXT) throws Exception {
		switch (clientPkt.getType()) {
		case SpecialClientPacket.SPECIAL_TYPE_CONNECTION_REQUEST:
			ConnectionRequestClientPacket crcp = (ConnectionRequestClientPacket) clientPkt; 
			short clientId = getOpenClientId();
			int mtu = Math.min(crcp.getMaxMTU(), serverOpts.mtu);
			boolean tcp = (crcp.getRequestFlags() & ConnectionRequestClientPacket.FLAG_TCP) != 0;
			int truncationAllowance = crcp.getTruncationAllowance();
			
			PortCommunicator comm;
			if (tcp) {
				Socket tcpSock = new Socket(serverOpts.forwardAddr, serverOpts.forwardPort);
				tcpSock.setTcpNoDelay(true);
				tcpSock.setReceiveBufferSize(Common.DEFAULT_MTU);
				comm = new TCPPortCommunicator(tcpSock);
				Log.get().println(Log.LEVEL_INFO, "New tcp client connection:" + (clientId & 0xffff));
			} else {
				UDPPortCommunicator udpComm = new UDPPortCommunicator(new DatagramSocket(0), mtu, crcp.getLateTimer()*2, false); 
				udpComm.addPortForward(forwardAddress);
				comm = udpComm;
				Log.get().println(Log.LEVEL_INFO, "New udp client connection:" + (clientId & 0xffff));
			}
			
			ConnectionState newConnection = new ConnectionState(clientId, 
					new LZMAPacketConverter(),
					new ServerPacketFactory(serverOpts.domain,
							truncationAllowance,
							isTXT ? Packet.ENC_TXT : Packet.ENC_SAFE16),

					comm,
					crcp.getLateTimer(),
					//never throttle the server
					false);
			
			//start listening AFTER the listeners have been added by the connection state
			comm.start();
			
			synchronized (clients) {
				clients.put(clientId, newConnection);
			}
			ConnectionAcceptedServerPacket cap = new ConnectionAcceptedServerPacket(clientId, mtu, crcp.getChallengeId());
			return cap;
		case SpecialClientPacket.SPECIAL_TYPE_CONNECTION_CLOSED:
			ConnectionClosedClientPacket closeReq = (ConnectionClosedClientPacket) clientPkt; 
			ConnectionState client = removeClient(closeReq.getClientID());
			if (client != null) {
				client.close();
			}
			//whether or not the client existed, respond with close
			ConnectionClosedServerPacket conClose = new ConnectionClosedServerPacket(closeReq.getClientID());
			return conClose;
		default:
		}
		throw new ProtocolException("Unknown special packet type");
	}

	private ConnectionState getClient(short clientId) {
		synchronized (clients) {
			return clients.get(clientId);
		}
	}
	
	private ConnectionState removeClient(short clientID) {
		synchronized (clients) {
			return clients.remove(clientID);
		}
	}

	//TODO remove a synchronization somewhere
	private short getOpenClientId() throws Exception {
		synchronized (clients) {
			int start = this.rand.nextInt(65536);
			for (int i=0; i<65536; i++) {
				if (null == getClient((short)(start+i))) {
					return (short)(start + i);
				}
			}
		}
		throw new Exception("No Open Client Slots!?");
	}
	
	private static int determineMaxResponseLength(Socket s, OPTRecord queryOPT) {
		/*int maxLength;
		if (s != null) {
			maxLength = 65535;
		} else if (queryOPT != null) {
			maxLength = Math.max(queryOPT.getPayloadSize(), 512);
		} else {
			maxLength = 512;
		}
		return maxLength;
		*/
		return 512;
	}

	byte[] buildErrorMessage(Header header, int rcode, Record question) {
		Message response = new Message();
		response.setHeader(header);
		for (int i = 0; i < 4; i++)
			response.removeAllRecords(i);
		if (rcode == Rcode.SERVFAIL)
			response.addRecord(question, Section.QUESTION);
		header.setRcode(rcode);
		header.setFlag(Flags.RD);
		header.setFlag(Flags.RA);
		header.setFlag(Flags.AA);
		header.setFlag(Flags.QR);
		return response.toWire();
	}

	public byte[] formerrMessage(byte[] in) {
		Header header;
		try {
			header = new Header(in);
		} catch (IOException e) {
			return null;
		}
		return buildErrorMessage(header, Rcode.FORMERR, null);
	}

	public byte[] errorMessage(Message query, int rcode) {
		return buildErrorMessage(query.getHeader(), rcode, query.getQuestion());
	}
	
	private void serveDNS(InetAddress addr, int port) {
		try {
			
			DatagramChannel chan = DatagramChannel.open();
			chan.socket().bind(new InetSocketAddress(addr, port));
			
			serveDNS(chan);
		}
		catch (IOException e) {
			Log.get().exception(Log.LEVEL_INFO, e);
		}
	}
	
	private void serveDNS(DatagramChannel chan) {
		ByteBuffer in = ByteBuffer.allocate(512);
		
		try {
			while (true) {
				SocketAddress from;
				
				try {
					from = chan.receive(in);
				} catch (InterruptedIOException e) {
					continue;
				}
				
				// We are done receiving into this buffer
				in.flip();
				// Get just the used bytes
				byte[] request = in.slice().array();
				byte[] response = null;
				try {
					Message query = new Message(request);
					//System.out.println(query.toString());
					response = generateReply(query, null);
				} catch (IOException e) {
					Log.get().exception(Log.LEVEL_ERROR, e);
					response = formerrMessage(request);
				}

				//no response? dont send anything
				if (response == null) {
					continue;
				}
				
				//System.out.println("sending to:" + dpOut.getAddress() + " port:" + dpOut.getPort());
				chan.send(ByteBuffer.wrap(response), from);
				
				// clear the buffer
				in.clear();
			}
		} catch (IOException e) {
			Log.get().exception(Log.LEVEL_INFO, e);
		}

	}
	
	public void addUDP(final InetAddress addr, final int port) {
		Thread t = new Thread("DNS Serve " + addr + ":" + port) {
			public void run() {
				serveDNS(addr, port);
			}
		};
		t.start();
	}

	private class ClientTimeoutThread extends Thread {
		private int idleCount = 0;
		
		public ClientTimeoutThread() {
			super("Client timeout");
		}

		@Override
		public void run() {
			while (true) {
				try {
					sleep(1000);
				} catch (InterruptedException e) {
				}
				
				timeoutClients();
				timeoutServer();
			}
		}
		
		private void timeoutClients() {
			synchronized (clients) {
				Collection<ConnectionState> c = clients.values();
				Iterator<ConnectionState> iter = c.iterator();
				while (iter.hasNext()) {
					ConnectionState client = iter.next();
					if (client.hasTimedOut(Common.CONN_TIMEOUT)) {
						if (client.hasSentConnectionClosed()) {
							Log.get().println(Log.LEVEL_INFO, "Client connection closed (ClientID:" + (0xFFFF & client.getClientId()) + ").");
						} else {
							Log.get().println(Log.LEVEL_INFO, "Client timeout (ClientID:" + (0xFFFF & client.getClientId()) + ").");
						}
						client.close();
						iter.remove();
					}
				}
			}
		}

		private int timeoutServer() {
			if (serverOpts.idleTimeout > 0) {
				int numClients;
				synchronized (clients) {
					numClients = clients.size();
				}
				
				if (numClients > 0) {
					Log.get().println(Log.LEVEL_ULTRASPAM, "Number of clients > 0. Not idle.");
					idleCount = 0;
				} else {
					idleCount++;
					Log.get().println(Log.LEVEL_SPAM, "Idle for " + idleCount + "s");
					if (idleCount > serverOpts.idleTimeout) {
						Log.get().println(Log.LEVEL_SPAM, "Idle timeout exceeded. Exiting.");
						System.exit(0);
					}
				}
			}
			return idleCount;
		}
	}

}

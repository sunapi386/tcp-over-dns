package client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Vector;

import org.xbill.DNS.ResolverConfig;

import common.Common;
import common.CommonCmdLineParser;
import common.CommonOptions;
import common.Packet;

public class ClientCmdLineParser extends CommonCmdLineParser {
	private Option listenAddr;
	private Option listenPort;
	private Option dnsServer;
	private Option dnsRequestAddress;
	private Option interval;
	private Option truncationAllowance;
	private Option queryType;
	private Option clientEnc;
	
	public ClientCmdLineParser() {
		listenAddr = addStringOption("listen-address");
		listenPort = addIntegerOption("listen-port");
		dnsServer = addStringOption("dns-server");
		interval = addIntegerOption("interval");
		truncationAllowance = addIntegerOption("trunc-bytes");
		queryType = addStringOption("query-type");
		clientEnc = addStringOption("client-enc");
		
		//hidden option that probably no one will want to use
		dnsRequestAddress = addStringOption("dns-request-address");
	}

	public ClientOptions parseCmdLine(String args[]) {
		CommonOptions commonOpts = super.parseCmdLine(args);
		if (commonOpts == null) {
			return null;
		}
		
		try {
			ClientOptions result = new ClientOptions();
			result.domain = commonOpts.domain;
			result.mtu = commonOpts.mtu;
			result.logLevel = commonOpts.logLevel;
			result.logFile = commonOpts.logFile;
			
			String listenAddrString = (String)getOptionValue(listenAddr);
			if (listenAddrString != null) {
				result.listenAddr = InetAddress.getByName(listenAddrString);
			}
			Integer iListenPort = (Integer) getOptionValue(listenPort);
			
			if (iListenPort == null) {
				result.listenPort = -1;
			} else {
				result.listenPort = iListenPort;
			}
			
			result.dnsServers = new ArrayList<InetAddress>();
			Vector<?> dnsServers = getOptionValues(dnsServer);
			for (int i=0; i<dnsServers.size(); i++) {
				String dnsServerString = (String)dnsServers.get(i);
				result.dnsServers.add(InetAddress.getByName(dnsServerString));
			}
			
			String dnsReqAddr = (String)getOptionValue(dnsRequestAddress);
			if (dnsReqAddr != null) {
				result.dnsRequestAddress = InetAddress.getByName(dnsReqAddr);
			}
			
			Integer iInterval = (Integer)getOptionValue(interval);
			if (iInterval != null) {
				result.interval = iInterval;
			}	
			
			Integer iTruncationBytes = (Integer)getOptionValue(truncationAllowance);
			if (iTruncationBytes == null) {
				result.truncationAllowance = -1;
			} else {
				result.truncationAllowance = iTruncationBytes;
			}
			
			String queryTypeStr = (String)getOptionValue(queryType);
			if (queryTypeStr != null) {
				queryTypeStr = queryTypeStr.toUpperCase();
				if (queryTypeStr.equals("CNAME")) {
					result.queryType = ClientOptions.CNAME_QUERIES;
				} else if (queryTypeStr.equals("TXT")) {
					result.queryType = ClientOptions.TXT_QUERIES;
				} else {
					System.err.println("query-type must be either \"A\" or \"TXT\".");
					return null;
				}
			}
			
			String clientEncStr = (String)getOptionValue(clientEnc);
			if (clientEncStr != null) {
				clientEncStr = clientEncStr.toLowerCase();
				if (clientEncStr.equals("base63")) {
					result.clientEnc = Packet.ENC_FULL63;
				} else if (clientEncStr.equals("hexhack37")) {
					result.clientEnc = Packet.ENC_HEX0x20;
				} else if (clientEncStr.equals("base16")) {
					result.clientEnc = Packet.ENC_SAFE16;
				} else {
					System.err.println("client-enc must be either \"base63\", \"base16\" or \"hexhack37\".");
					return null;
				}
			}
			
			
			if (!setDefaults(result)) {
				return null;
			}
			
			if (result.domain == null) {
				return null;
			}

			if (result.listenPort <= 0 || result.listenPort >= 65536) {
				return null;
			}
			
			if (result.truncationAllowance < 0 || result.truncationAllowance > 100) {
				return null;
			}
			
			return result;
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
			return null;
		}
	}

	private static boolean setDefaults(ClientOptions result) throws UnknownHostException {
		if (result.listenPort < 0) {
			result.listenPort = Client.DEFAULT_PORT;
		}
		if (result.listenAddr == null) {
			result.listenAddr = InetAddress.getByName(Client.DEFAULT_ADDR);
		}
		if (result.mtu <= 0) {
			result.mtu = Common.DEFAULT_MTU;
		}
		if (result.dnsServers.size() == 0) {
			String [] servers = ResolverConfig.getCurrentConfig().servers(); 
			
			if (servers != null) {
				for (int i=0; i<servers.length; i++) {
					try {
						result.dnsServers.add(InetAddress.getByName(servers[i]));
						//System.out.println("Adding dns server:" + servers[i]);
						break;
					} catch (IOException e) {
						System.err.println("Failed to lookup server:" + servers[i]);
					}
				}
			}
			
			//we couldnt autoconfig the dns server
			if (result.dnsServers.size() == 0) {
				System.err.println("Failed to automatically find dns server(s).");
				return false;
			}
		}
		
		if (result.dnsRequestAddress == null) {
			result.dnsRequestAddress = InetAddress.getByAddress(new byte[]{0, 0, 0, 0});
		}
		
		if (result.interval <= 0) {
			result.interval = Client.DEFAULT_INTERVAL;
		}
		
		if (result.logLevel < 0) {
			result.logLevel = Common.DEFAULT_LOGLEVEL;
		}
		
		if (result.truncationAllowance < 0) {
			result.truncationAllowance = Client.DEFAULT_TRUNCATION_ALLOWANCE;
		}
		
		if (result.queryType == 0) {
			result.queryType = ClientOptions.TXT_QUERIES;
		}
		
		if (result.clientEnc == 0) {
			result.clientEnc = common.Packet.ENC_FULL63;
		}
		
		return true;
		
	}

	public void printUsage() {
		System.err.println("Usage: java -jar " + Client.CLIENT_NAME + ".jar --domain tnl.example.com <options>");
		System.err.println("Available options:");
		System.err.println("--domain         a.b.com  The domain name that the dns server is sitting behind.");
		System.err.println("                          This must match the server's domain argument.");		
		System.err.println("--dns-server     a.b.c.d  Add a dns server to tunnel through. Overrides default");
		System.err.println("                          servers. This option may be specified multiple times.");
		System.err.println("--listen-port    portNum  The port the client should listen on. Defaults to");
		System.err.println("                          port " + Client.DEFAULT_PORT +".");
		System.err.println("--listen-address a.b.c.d  The address on which to listen for local connections.");
		System.err.println("                          Defaults to all addresses.");
		System.err.println("--interval       time     The delay between sending packets, in milliseconds.");
		System.err.println("                          Defaults to " + Client.DEFAULT_INTERVAL + ".");
		System.err.println("--mtu            bytes    Set the udp maximum MTU. Defaults to " + Common.DEFAULT_MTU + ".");
		System.err.println("--trunc-bytes    bytes    Tell the server to prevent truncation by sending");
		System.err.println("                          less data when answering queries. Defaults to " + Client.DEFAULT_TRUNCATION_ALLOWANCE + ".");
		System.err.println("--query-type     method   Set the request type: TXT or CNAME");
		System.err.println("                          Defaults to TXT. CNAME is much slower.");
		System.err.println("--client-enc     method   Set the client encoding: base63, base16, hexhack37.");
		System.err.println("                          Defaults to base63.");
		System.err.println("--log-file       file     Instead of logging to std err, log to this file.");
		System.err.println("--log-level      0-5      The amount of information to display. Defaults to 3.");
		System.err.println("                          0: Display nothing.");
		System.err.println("                          1: Display errors.");
		System.err.println("                          2: Display errors, warnings.");
		System.err.println("                          3: Display errors, warnings, general information.");
		System.err.println("                          4: Spam.");
		System.err.println("                          5: Megaspam.");
	}
	
	public static String encodingToString(byte enc) {
		switch (enc) {
		case Packet.ENC_FULL63:
			return "base63";
		case Packet.ENC_SAFE16:
			return "base16";
		case Packet.ENC_HEX0x20:
			return "hexhack37";
		case Packet.ENC_TXT:
			return "text";
		default:
			return null;
		}
	}
}

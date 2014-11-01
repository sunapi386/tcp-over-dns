package server;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.channels.Channel;
import java.nio.channels.DatagramChannel;
import java.util.Vector;

import common.Common;
import common.CommonCmdLineParser;
import common.CommonOptions;

public class ServerCmdLineParser extends CommonCmdLineParser {
	private Option dnsAddr;
	private Option dnsPort;
	private Option fwdAddr;
	private Option fwdPort;
	private Option idleTimeout;
	
	public ServerCmdLineParser() {
		dnsAddr = addStringOption("dns-address");
		dnsPort = addIntegerOption("dns-port");
		fwdAddr = addStringOption("forward-address");
		fwdPort = addIntegerOption("forward-port");
		idleTimeout = addIntegerOption("idle-timeout");
	}

	@SuppressWarnings("unchecked")
	public ServerOptions parseCmdLine(String args[]) {
		CommonOptions commonOpts = super.parseCmdLine(args);
		if (commonOpts == null) {
			return null;
		}
		try {
			ServerOptions result = new ServerOptions();
			result.domain = commonOpts.domain;
			result.mtu = commonOpts.mtu;
			result.logLevel = commonOpts.logLevel;
			result.logFile = commonOpts.logFile;
			
			Vector<?> listenStrings = getOptionValues(dnsAddr);
			for (Object listenStringObj : listenStrings) {
				result.listenAddrs.add(InetAddress.getByName((String)listenStringObj));
			}
			result.listenPorts.addAll(getOptionValues(dnsPort));
			String forwardAddr = (String)getOptionValue(fwdAddr);
			if (forwardAddr != null) {
				result.forwardAddr = InetAddress.getByName(forwardAddr);
			}
			Integer forwardPort = (Integer)getOptionValue(fwdPort);
			if (forwardPort == null) {
				result.forwardPort = -1;
			} else {
				result.forwardPort = forwardPort;
			}

			Integer idleTimeout = (Integer) getOptionValue(this.idleTimeout);
			if (idleTimeout == null) {
				result.idleTimeout = -1;
			} else {
				result.idleTimeout = idleTimeout;
			}
			
			setDefaults(result);

			//we do this here because we depend on this
			//before we can print to stdout
			result.inheritedChannel = inheritedDatagramChannel();
			
			if (result.domain == null) {
				return null;
			}
			if (result.forwardPort <= 0) {
				return null;
			}
			
			return result;
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
			return null;
		}
	}

	private DatagramChannel inheritedDatagramChannel() {
		Channel chan;
		try {
			chan = System.inheritedChannel();
			//do we have a channel? and is it the right type?
			if (chan != null && chan instanceof DatagramChannel) {
				return (DatagramChannel)chan;
			}
		} catch (IOException e) {
		}
		return null;
	}

	private static void setDefaults(ServerOptions result) throws UnknownHostException {
		if (result.listenPorts.size() == 0) {
			result.listenPorts.add(Common.DEFAULT_DNS_PORT);
		}
		if (result.listenAddrs.size() == 0) {
			result.listenAddrs.add(InetAddress.getByName(Server.DEFAULT_ADDR));
		}
		if (result.forwardAddr == null) {
			result.forwardAddr = Inet4Address.getByAddress(new byte[]{ 127, 0, 0, 1});
		}
		if (result.mtu <= 0) {
			result.mtu = Common.DEFAULT_MTU;
		}
	}

	public void printUsage() {
		System.err.println("Usage: java -jar " + Server.SERVER_NAME + ".jar --domain tnl.example.com --forward-port N <options>");
		System.err.println("Available options:");
		System.err.println("--domain          a.b.com  The domain name that the tunnel is sitting behind.");
		System.err.println("                           This must match the client's domain argument.");
		System.err.println("--forward-port    portNum  The port of the service which we are exporting, it");
		System.err.println("                           will be provided through the tunnel to the clients.");
		System.err.println("--forward-address a.b.c.d  The address of the service which we are exporting,");
		System.err.println("                           Defaults to localhost.");
		System.err.println("--dns-port        portNum  The port on which the server should listen for dns");
		System.err.println("                           queries. Defaults to port " + Common.DEFAULT_DNS_PORT + ".");
		System.err.println("--dns-address     a.b.c.d  The address on which to listen for dns queries.");
		System.err.println("                           Defaults to all addresses.");
		System.err.println("--mtu             bytes    Set the udp maximum MTU. Defaults to " + Common.DEFAULT_MTU + ".");
		System.err.println("--idle-timeout    seconds  Close the server after this much time elapses");
		System.err.println("                           without a client connecting. Disabled by default.");
		System.err.println("--log-file        file     Instead of logging to std err, log to this file.");
		System.err.println("--log-level       0-5      The amount of information to display. Defaults to 3.");
		System.err.println("                           0: Display nothing.");
		System.err.println("                           1: Display errors.");
		System.err.println("                           2: Display errors, warnings.");
		System.err.println("                           3: Display errors, warnings, general information.");
		System.err.println("                           4: Spam.");
		System.err.println("                           5: Megaspam.");
	}
}

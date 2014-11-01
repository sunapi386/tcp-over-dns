package server;
import java.net.InetAddress;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;

import common.CommonOptions;

public class ServerOptions extends CommonOptions {
	public ArrayList<InetAddress> listenAddrs = new ArrayList<InetAddress>();
	public ArrayList<Integer> listenPorts = new ArrayList<Integer>();
	//public InetAddress dnsServer;
	public InetAddress forwardAddr;
	public int forwardPort;
	public int idleTimeout;
	public DatagramChannel inheritedChannel;
}

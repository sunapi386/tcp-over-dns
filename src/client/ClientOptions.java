package client;

import java.net.InetAddress;
import java.util.ArrayList;

import common.CommonOptions;

public class ClientOptions extends CommonOptions {
	public static final int CNAME_QUERIES = 1;
	public static final int TXT_QUERIES   = 2;
	
	public int listenPort;
	public InetAddress listenAddr;
	public ArrayList<InetAddress> dnsServers;
	public InetAddress dnsRequestAddress;
	public int interval;
	public int truncationAllowance;
	public int queryType;
	public byte clientEnc;
}

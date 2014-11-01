package common;


public class Common {
	public static final String NAME = "tcp-over-dns";
	public static final int DEFAULT_MTU      = 1500;
	public static final int DEFAULT_DNS_PORT = 53;
	public static final int DEFAULT_TTL      = 30;
	public static final int DEFAULT_LOGLEVEL = 3;
	/**
	 * tcp-over-dns client-to-server and server-to-client timeout, in ms. 
	 * dont raise this too high because currently each stream splits bandwidth 
	 */
	public static final int CONN_TIMEOUT     = 45000;
	/** since udp has no 'connections' a udp port will timeout and close after this amount of time, in ms. */
	public static final int UDP_TIMEOUT      = 30000;
	
}

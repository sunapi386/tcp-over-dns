package common;

public class DNSSizes {
	//max length for any one name is 255
	public static final int MAX_DOMAIN_LEN = 255;
	//max length for any one label (subdomain) is 63
	public static final int MAX_DOMAIN_LABEL = 63;
	
	public static final int MAX_PACKET_SIZE = 512;
	//12 for the dns header, 4 for the udp header
	public static final int DNS_HEADER_SIZE = 12 + 4;
	//sometimes servers like modifying our packets a bit
	public static final int DNS_FUDGE_FACTOR = 20;
	
}

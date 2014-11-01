package common;

import java.io.IOException;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import org.xbill.DNS.CNAMERecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;

/**
 * A packet looks like this
 * ________________________________________________________________________________________
 * |   8bits  |   8bits  |   8bits  |   8bits  |   8bits  |          multibyte            |
 * | clientID | packetID |oldestSeen|   flags  | checksum |        packet content         |
 * ````````````````````````````````````````````````````````````````````````````````````````
 */
public abstract class Packet  {
	//TODO: check if decoded size is buggy in this object
	protected final static BaseConverter base63Converter = new BaseConverter("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-");
	protected final static BaseConverter hex0x20HackConverter = new BaseConverter("abcdefghijklmnopqrstuvwxyz0123456789-");
	//rfc 1035 <character-string>

	protected final static BaseConverter txtConverter;
	static {
		ArrayList<Byte> legalChars = new ArrayList<Byte>();
		for (int i=0; i<256; i++) {
			if (i <= 31 || i >= 127) {
				//everything here keeps getting escaped in the pipeline
				//seems to be the bind server, but possibly not
				continue;
			}

			switch (i) {
			case 0:    //introduces some problems with java strings
			case '"':  //we will go with the quoted string method, so a quote uses 2 bytes
			case '\\': //escape char
			case '(':  //used to group data, we dont want this to be parsed
			case ')':  //ditto
			case '@':  //used to identify origin, we maybe could leave this in
			case ';':  //used to identify comments, gets escaped
			case ' ':  //spaces can get eaten

			//while not listed in the spec tilde still causes problems
			case '~':  //this causes data corruption, something special cases it somewhere (probably bind)


			//cant figure out what is wrong, keep adding more chars
		//	case '`':
		//	case '\'':

				continue;
			}
			legalChars.add((byte)i);
		}
		byte [] chars = new byte[legalChars.size()];
		for (int i=0; i<chars.length; i++) {
			chars[i] = legalChars.get(i);
		}

		txtConverter = new BaseConverter(chars);
	}

	/** The client sender's id */
	private short clientID;
	/** The packet id of the dns message sent through the internet */
	private byte packetID;
	/** A checksum of everything summed */
	private byte checksum;
	/** The last packet id seen from the remote host */
	private byte lastPidSeen;
	/** Flags for this packet's content */
	private byte flags;
	/** bytes of data */
	private byte [] dataContent;

	private byte encodingBase;

	//byte index offsets
	public static final int OFFSET_CLIENTID      = 0;
	public static final int OFFSET_PACKETID      = 2;
	public static final int OFFSET_LAST_PID_SEEN = 3;
	public static final int OFFSET_FLAGS         = 4;
	public static final int OFFSET_CHECKSUM      = 5;
	public static final int FIXED_HEADER_SIZE    = 6;

	public static final byte ENC_SAFE16  = 1;
	public static final byte ENC_FULL63  = 2;
	public static final byte ENC_TXT     = 3;
	public static final byte ENC_HEX0x20 = 4;

	// flags
	/** ends this packet in the data stream, assemble it now */
	public static final byte FLAG_END_ASSEMBLY = (1 << 0);
	/** Non-data stream packet, do not manage it or ack it */
	public static final byte FLAG_NO_OP        = (1 << 1);
	/** Non-data stream message (establish connection etc) */
	public static final byte FLAG_SPECIAL      = (1 << 2);
	/** The assembly is compressed */
	public static final byte FLAG_COMPRESSED   = (1 << 3);

	/** This is the last write packet in the data stream (TCP only) */
	public static final byte FLAG_END_WRITE    = (1 << 5);
	/** This is the last read packet in the data stream (TCP only) */
	public static final byte FLAG_END_READ     = (1 << 6);

	/**
	 * @param clientID The sender's client  id
	 * @param packetID The packet id of the dns message sent through the internet
	 * @param checksum2
	 * @param dataContent The data of the packet, this variable is adopted.
	 */
	protected Packet(short clientID, byte packetID, byte flags, byte encBase, byte lastPidSeen, byte checksum, byte[] dataContent) {
		this();
		setAllFields(clientID, packetID, flags, lastPidSeen, checksum, dataContent);
		setEncodingBase(encBase);
	}

	protected Packet(byte [] rawData) throws ProtocolException {
		this();
		decodePacket(rawData);
	}

	protected Packet() {
	}

	protected void decodePacket(byte [] rawBytes) throws ProtocolException {
		if (rawBytes.length < FIXED_HEADER_SIZE) {
			throw new ProtocolException("Packet too short");
		}

		short clientID = (short) readShortFromBytes(rawBytes, OFFSET_CLIENTID);
		byte packetID = rawBytes[OFFSET_PACKETID];
		byte lastPidSeen = rawBytes[OFFSET_LAST_PID_SEEN];
		byte flags = rawBytes[OFFSET_FLAGS];
		byte checksum = rawBytes[OFFSET_CHECKSUM];

		int contentLength = rawBytes.length - FIXED_HEADER_SIZE;
		byte [] dataContent = new byte[contentLength];
		System.arraycopy(rawBytes, FIXED_HEADER_SIZE, dataContent, 0, contentLength);

		this.setAllFields(clientID, packetID, flags, lastPidSeen, checksum, dataContent);
	}

	protected byte [] encodePacket() throws ProtocolException {
		byte [] rawBytes = new byte[getDataContent().length + FIXED_HEADER_SIZE];
		writeShortToBytes(rawBytes, OFFSET_CLIENTID, getClientID());
		rawBytes[OFFSET_PACKETID] = getPacketID();
		rawBytes[OFFSET_LAST_PID_SEEN] = getLastPidSeen();
		rawBytes[OFFSET_FLAGS] = getFlags();
		rawBytes[OFFSET_CHECKSUM] = getChecksum();

		System.arraycopy(getDataContent(), 0, rawBytes, FIXED_HEADER_SIZE, getDataContent().length);
		return rawBytes;
	}

	protected static int dynamicHeaderSize(int ackCount) {
		return FIXED_HEADER_SIZE + ackCount * 2;
	}

	protected static String removeDots(String source) {
		//works fine but is not backwards compatible with old vms
		//toParse = toParse.replaceAll("\\.", "");

		StringBuffer buff = new StringBuffer(source);
		int len = buff.length();
		for (int i=len-1; i>=0; i--) {
			if (buff.charAt(i) == '.') {
				buff.deleteCharAt(i);
			}
		}
		return buff.toString();
	}

	public boolean isChecksumValid() {
		return (checksum == calcChecksum(clientID, packetID, lastPidSeen, flags, dataContent));
	}

	public void updateChecksum() {
		checksum = calcChecksum(clientID, packetID, lastPidSeen, flags, dataContent);
	}

	public byte getChecksum() {
		return checksum;
	}

	public void setClientID(byte clientID) {
		this.clientID = clientID;
	}

	public void setPacketID(byte packetID) {
		this.packetID = packetID;
	}

	public void setContents(byte[] dataContent) {
		this.dataContent = dataContent;
	}

	public short getClientID() {
		return clientID;
	}

	public byte getPacketID() {
		return packetID;
	}

	public byte [] getDataContent() {
		return dataContent;
	}

	private static byte calcChecksum(short clientID, byte packetID, byte lastPidSeen, byte flags, byte [] contents) {
		int sum = clientID + packetID + flags + lastPidSeen;
		sum += contents.length;
		for (byte b : contents) {
			sum += b;
		}
		return (byte)-sum;
	}

	/**
	 * Sets all fields EXCEPT checksum
	 * @param clientID
	 * @param packetID
	 * @param assemblyID
	 * @param flags
	 * @param lastPidSeen
	 * @param dataContent
	 */
	public void setAllFields(short clientID, byte packetID, byte flags, byte lastPidSeen, byte checksum, byte[] dataContent) {
		this.clientID = clientID;
		this.packetID = packetID;
		this.flags = flags;
		this.lastPidSeen = lastPidSeen;
		this.checksum = checksum;
		this.dataContent = dataContent;
	}

	public void setChecksum(byte checksum) {
		this.checksum = checksum;
	}

	public void setDataContent(byte[] dataContent) {
		this.dataContent = dataContent;
	}

	public byte getLastPidSeen() {
		return lastPidSeen;
	}

	public void setLastPidSeen(byte lastPidSeen) {
		this.lastPidSeen = lastPidSeen;
	}

	public byte getFlags() {
		return flags;
	}

	public void setFlags(byte flags) {
		this.flags = flags;
	}

	public boolean isAssemblyEnd() {
		return 0 != (this.flags & FLAG_END_ASSEMBLY);
	}

	public boolean isNoOp() {
		return 0 != (this.flags & FLAG_NO_OP);
	}

	public boolean isSpecial() {
		return 0 != (this.flags & FLAG_SPECIAL);
	}

	public boolean isCompressed() {
		return 0 != (this.flags & FLAG_COMPRESSED);
	}

	public int getEncodingBase() {
		return encodingBase;
	}

	public void setEncodingBase(byte encodingBase) {
		this.encodingBase = encodingBase;
	}

	public static final int readIntFromBytes(byte [] data, int offset) {
		int ch0 = (data[offset+0]      ) & 0x000000ff;
		int ch1 = (data[offset+1] <<  8) & 0x0000ff00;
		int ch2 = (data[offset+2] << 16) & 0x00ff0000;
		int ch3 = (data[offset+3] << 24) & 0xff000000;
		return ch0 | ch1 | ch2 | ch3;
	}

	public static final void writeIntToBytes(byte [] data, int offset, int value) {
		byte ch0 = (byte)value;
		byte ch1 = (byte)(value >>  8);
		byte ch2 = (byte)(value >> 16);
		byte ch3 = (byte)(value >> 24);
		data[offset+0] = ch0;
		data[offset+1] = ch1;
		data[offset+2] = ch2;
		data[offset+3] = ch3;
	}

	public static final int readShortFromBytes(byte [] data, int offset) {
		int ch0 = (data[offset+0]      ) & 0x000000ff;
		int ch1 = (data[offset+1] <<  8) & 0x0000ff00;
		return ch0 | ch1;
	}

	public static final void writeShortToBytes(byte [] data, int offset, int value) {
		byte ch0 = (byte)value;
		byte ch1 = (byte)(value >>  8);
		data[offset+0] = ch0;
		data[offset+1] = ch1;
	}


	protected static String stringToDomain(String nameToSplit, String domain) {
		//its only possible to add ceil(255 / 63) = 5 dots
		StringBuffer nameAsString = new StringBuffer(nameToSplit.length() + 5);

		int begin = 0;
		int end = DNSSizes.MAX_DOMAIN_LABEL;
		while (begin < nameToSplit.length()) {
			end = Math.min(end, nameToSplit.length());
			nameAsString.append(nameToSplit, begin, end);
			nameAsString.append('.');
			begin += DNSSizes.MAX_DOMAIN_LABEL;
			end += DNSSizes.MAX_DOMAIN_LABEL;
		}

		if (domain != null && !domain.equals("")) {
			nameAsString.append(domain);
			nameAsString.append('.');
		}
		return nameAsString.toString();
	}

	public final Name encodeAsName(String domain) throws ProtocolException {
		assert (isChecksumValid() == true);

		byte [] rawBytes = encodePacket();

		String nameToSplit;

		switch (getEncodingBase()) {
		default:
		case ENC_SAFE16:
			//the 'a' says its type ENC_SAFE16
			nameToSplit = 'a' + new String(Base16Alpha.encodeBase16(rawBytes));
			break;
		case ENC_FULL63:
			//the 'b' says its type ENC_FULL63
			nameToSplit = 'b' + encodeLengthThenString(rawBytes, base63Converter);
			break;
		case ENC_HEX0x20:
			//the 'h' says its type ENC_HEX0x20
			nameToSplit = 'h' + encodeLengthThenString(rawBytes, hex0x20HackConverter);
			break;
		}

		String nameAsDomain = stringToDomain(nameToSplit, domain);

		assert nameAsDomain.length() <= DNSSizes.MAX_DOMAIN_LEN;
		try {
			return Name.fromString(nameAsDomain);
		} catch (TextParseException e) {
			Log.get().exception(Log.LEVEL_ERROR, e);
			ProtocolException pe = new ProtocolException("Name exception");
			throw pe;
		}
	}

	private String encodeLengthThenString(byte[] rawBytes, BaseConverter converter) {
		byte [] encBytes = new byte [converter.encodedSize(rawBytes.length) + converter.encodedSize(2) + 1];
		byte [] len = {(byte) rawBytes.length, (byte) (rawBytes.length>>8)};
		int numBytesEnc = converter.encode(len, 0, 2, encBytes, 0);
		numBytesEnc += converter.encode(rawBytes, 0, rawBytes.length, encBytes, numBytesEnc);

		return new String(encBytes, 0, numBytesEnc);
	}

	public TXTRecord encodeAsTxt(Name name, int dclass, long ttl) throws ProtocolException {
		assert (isChecksumValid() == true);
		return new TXTRecord(name, dclass, ttl, encodeAsString(txtConverter));
	}

	public CNAMERecord encodeAsCname(Name name, int dclass, long ttl) throws ProtocolException {
		assert (isChecksumValid() == true);
		return new CNAMERecord(name, dclass, ttl, encodeAsName(null));
	}

	private String encodeAsString(BaseConverter baseConverter) throws ProtocolException {
		byte [] rawBytes = encodePacket();

		byte [] encBytes = new byte [baseConverter.encodedSize(rawBytes.length) + baseConverter.encodedSize(2)];
		byte [] len = {(byte) rawBytes.length, (byte) (rawBytes.length>>8)};
		int numBytesHeader = baseConverter.encode(len, 0, 2, encBytes, 0);
		int numBytesData = baseConverter.encode(rawBytes, 0, rawBytes.length, encBytes, numBytesHeader);

		return new String(encBytes, 0, numBytesHeader + numBytesData);
	}

	@Override
	public String toString() {
		String flags = (isAssemblyEnd()?" ASM_END":"") + (isNoOp()?" NO_OP":"") + (isSpecial()?" SPCL":"" + (isCompressed()?" LZMA":"") + (isEndOfWrite() ? " END_WR":"") + (isEndOfRead() ? " END_RD":""));

		return "Packet[ClientID:" + (this.getClientID() & 0xFFFF) +
			" PacketID:" + (this.getPacketID() & 0xFF) +
			" Flags:" + flags + " Data:" + getDataContent().length + "bytes ack:" + (getLastPidSeen()&0xFF) + " checksum:" + (isChecksumValid()?"ok":"bad") + "]";
	}

	/**
	 * @param domain
	 * @return Number of bytes that could fit in a client packet.
	 */
	public static int maxDataLength(String domain, int truncationAllowance, int encodingType) {

		//this calculation was 1-way
		/*
		int remaining = MAX_DOMAIN_LEN - (domain.length() + 1); // .domain.com
		// we have to account for the periods between labels
		int numChars = remaining - (remaining / MAX_DOMAIN_LABEL + 1);
		//we currently use base16 (4bit), which means we can send half as many chars as are available
		return numChars / 2;
		*/


		//               full udp packet              fixed size             for other headers           server sends both Q&A      encoding type header
		int remaining = DNSSizes.MAX_PACKET_SIZE - DNSSizes.DNS_HEADER_SIZE - truncationAllowance - DNSSizes.DNS_FUDGE_FACTOR - (domain.length() + 2)*2 - 2;
		int numChars = remaining - (remaining / DNSSizes.MAX_DOMAIN_LABEL + 1);
		//numChars / 2 because we need to reply with question+answer
		//         / 2 again because we currently use base16 (4bit), which means we can send half as many chars as are available

		//divided in half because half the chars need to go to the response
		numChars /= 2;

		switch (encodingType) {
		case ENC_SAFE16:
			return numChars / 2;
		case ENC_FULL63:
			return base63Converter.decodedSize(numChars);
		case ENC_HEX0x20:
			return hex0x20HackConverter.decodedSize(numChars);
		case ENC_TXT:
			return txtConverter.decodedSize(numChars);
		default:
			return -1;
		}

	}

	public boolean isEndOfWrite() {
		return (getFlags() & FLAG_END_WRITE) != 0;
	}

	public boolean isEndOfRead() {
		return (getFlags() & FLAG_END_READ) != 0;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Packet other = (Packet) obj;
		if (clientID != other.clientID)
			return false;
		if (!Arrays.equals(dataContent, other.dataContent))
			return false;
		if (encodingBase != other.encodingBase)
			return false;
		if (flags != other.flags)
			return false;
		if (lastPidSeen != other.lastPidSeen)
			return false;
		if (packetID != other.packetID)
			return false;
		return true;
	}

	public static byte [] decodeTxtRecord(TXTRecord record) throws IOException {
		byte [] decodeStringBytes = (byte[])record.getStringsAsByteArrays().get(0);

		byte [] pktSizeBytes = {0,0};
		int bytesRead = txtConverter.decode(decodeStringBytes, 0, decodeStringBytes.length, 2, pktSizeBytes);
		int pktSize = (pktSizeBytes[0] & 0xff) | ((pktSizeBytes[1] & 0xff) << 8);
		byte[] rawBytes = new byte[pktSize];
		txtConverter.decode(decodeStringBytes, bytesRead, decodeStringBytes.length - bytesRead, pktSize, rawBytes);

		return rawBytes;
	}

	public static byte [] decodeCnameRecord(CNAMERecord record) throws IOException {
		return Packet.decodeName(record.getAlias(), null);
	}

	public static byte [] decodeName(Name name, String domain) throws IOException {
		//the extra toLowerCase calls in here is to prevent against the 0x20 dns hack
		//from interfering with our data

		String toParse = name.toString();
		String toParseLower = toParse.toLowerCase(Locale.ENGLISH);
		if (domain != null && !domain.equals("")) {
			//remove the domain name leading and trailing
			int stripTo = toParseLower.lastIndexOf("." + domain.toLowerCase(Locale.ENGLISH) + ".");
			if (stripTo == -1) {
				Log.get().println(Log.LEVEL_ERROR, "Domain name missing from Name entry. (Name: \"" + name + "\")");
			} else {
				toParse = toParse.substring(0, stripTo);
				toParseLower = toParseLower.substring(0, stripTo);
			}
		}
		//remove all dots
		toParse = removeDots(toParse);
		toParseLower = removeDots(toParseLower);

		byte [] rawBytes;
		switch (Character.toLowerCase(toParse.charAt(0))) {
		case 'a':
			rawBytes = Base16Alpha.decodeBase16(toParseLower.getBytes(), 1);
			break;
		case 'b': //do not lowercase this one
			rawBytes = decodeLengthThenString(toParse.getBytes(), base63Converter);
			break;
		case 'h':
			rawBytes = decodeLengthThenString(toParseLower.getBytes(), hex0x20HackConverter);
			break;
		default:
			throw new ProtocolException("Bad encoding type");
		}

		if (rawBytes.length < FIXED_HEADER_SIZE) {
			throw new ProtocolException("Packet too small");
		}

		return rawBytes;
	}

	private static byte[] decodeLengthThenString(byte[] decodeStringBytes, BaseConverter converter)
			throws IOException {
		byte[] rawBytes;
		byte [] pktSizeBytes = {0,0};
		int bytesRead = converter.decode(decodeStringBytes, 1, decodeStringBytes.length - 1, 2, pktSizeBytes);
		int pktSize = (pktSizeBytes[0]&0xff) | ((pktSizeBytes[1]&0xff) << 8);
		rawBytes = new byte[pktSize];
		converter.decode(decodeStringBytes, 1 + bytesRead, decodeStringBytes.length - 1 - bytesRead, pktSize, rawBytes);
		return rawBytes;
	}

}
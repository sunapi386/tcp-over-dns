package common;

import java.net.ProtocolException;

public class ConnectionRequestClientPacket extends SpecialClientPacket {
	
	public static final int FLAG_TCP = (1 << 0);
	
	private static final int OFFSET_MTU = SPECIAL_HEADER_SIZE + 0;
	private static final int OFFSET_CHALLENGE = SPECIAL_HEADER_SIZE + 2;
	private static final int OFFSET_LATETIMER = SPECIAL_HEADER_SIZE + 6; 
	private static final int OFFSET_FLAGS = SPECIAL_HEADER_SIZE + 8;
	private static final int OFFSET_TRUNC_ALLOWANCE = SPECIAL_HEADER_SIZE + 9;
	public static final int MSG_LEN = SPECIAL_HEADER_SIZE + 11;
	
	public ConnectionRequestClientPacket(int maxMTU, int challengeId, int lateTimerMillis, int flags, short truncationAllowance) {
		super((byte)0, (byte)0, (byte) (Packet.FLAG_SPECIAL | Packet.FLAG_NO_OP), (byte)0, (byte)0, new byte [MSG_LEN]);
		setMaxMTU(maxMTU);
		setChallengeId(challengeId);
		setLateTimer(lateTimerMillis);
		setFlags(flags);
		setTruncationAllowance(truncationAllowance);
	}

	public ConnectionRequestClientPacket(byte [] data) throws ProtocolException {
		super(data);
	}
	
	public int getMaxMTU() {
		return readShortFromBytes(getDataContent(), OFFSET_MTU);
	}
	
	public void setMaxMTU(int maxMTU) {
		writeShortToBytes(getDataContent(), OFFSET_MTU, maxMTU);
	}
	
	public int getChallengeId() {
		return readIntFromBytes(getDataContent(), OFFSET_CHALLENGE);
	}
	
	public void setChallengeId(int challengeId) {
		writeIntToBytes(getDataContent(), OFFSET_CHALLENGE, challengeId);
	}
	
	public int getLateTimer() {
		return readShortFromBytes(getDataContent(), OFFSET_LATETIMER);
	}
	
	public void setLateTimer(int lateTimerMillis) {
		writeShortToBytes(getDataContent(), OFFSET_LATETIMER, lateTimerMillis);
	}

	public void setFlags(int flags) {
		getDataContent()[OFFSET_FLAGS] = (byte)flags;
	}
	
	public int getRequestFlags() {
		return getDataContent()[OFFSET_FLAGS] & 0xff;
	}
	
	public void setTruncationAllowance(int truncationAllowance) {
		writeShortToBytes(getDataContent(), OFFSET_TRUNC_ALLOWANCE, truncationAllowance);
	}
	
	public int getTruncationAllowance() {
		return readShortFromBytes(getDataContent(), OFFSET_TRUNC_ALLOWANCE);
	}

	@Override
	public String toString() {
		String tcpOrUDP = ((getRequestFlags() & FLAG_TCP) != 0) ? "TCP" : "UDP";
		return "ConnectionRequestClientPacket[type:" + tcpOrUDP + " challengeId:" + getChallengeId() + " MTU: " + getMaxMTU() +  " Packet:" + super.toString() + "]"; 
	}
	
}

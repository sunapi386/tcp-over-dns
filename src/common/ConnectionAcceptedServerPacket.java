package common;

import java.net.ProtocolException;

public class ConnectionAcceptedServerPacket extends SpecialServerPacket {

	public static final int MSG_LEN = SPECIAL_HEADER_SIZE + 6;
	private static final int OFFSET_MTU = SPECIAL_HEADER_SIZE + 0;
	private static final int OFFSET_CHALLENGE = SPECIAL_HEADER_SIZE + 2;
	
	/**
	 * @param clientId the new clientid of the connecting host
	 * @param maxMTU negotiated MTU
	 * @param challengeId copy of the challenge (in case of multiple clients)
	 */
	public ConnectionAcceptedServerPacket(short clientId, int maxMTU, int challengeId) {
		super(clientId, (byte)0, (byte) (Packet.FLAG_SPECIAL | Packet.FLAG_NO_OP), (byte)0, (byte)0, new byte [MSG_LEN]);
		setMaxMTU(maxMTU);
		setChallengeId(challengeId);
	}

	public ConnectionAcceptedServerPacket(byte [] data)
			throws ProtocolException {
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
	
	@Override
	public String toString() {
		return "ConnectionAcceptedServerPacket[challengeId:" + getChallengeId() + " MTU: " + getMaxMTU() +  " Packet:" + super.toString() + "]"; 
	}
	
}

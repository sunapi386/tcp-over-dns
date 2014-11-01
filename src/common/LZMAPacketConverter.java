package common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import SevenZip.Compression.LZMA.Decoder;
import SevenZip.Compression.LZMA.Encoder;

public class LZMAPacketConverter extends PacketConverter {

	Encoder encoder;
	Decoder decoder;
	public LZMAPacketConverter() {
		encoder = new Encoder();
		//look in 7z.chm to figure out what this stuff means
		
		// 2^6 = 64 bytes 
		encoder.SetDictionarySize(1<<7);
		encoder.SetEndMarkerMode(false);
		//larger is supposed to be better, just gonna let it default
		//encoder.SeNumFastBytes(256);
		//currently does nothing anyway
		encoder.SetAlgorithm(1);
		//these are fine, let them default
		//encoder.SetLcLpPb(lc, lp, pb)
		//this is undocumented, let it default
		//encoder.SetMatchFinder();
		
		ByteArrayOutputStream coderProps =  new ByteArrayOutputStream(5);
		try {
			encoder.WriteCoderProperties(coderProps);
		} catch (IOException e) {
			Log.get().println(Log.LEVEL_ERROR, "LZMA fatal error.");
			Log.get().exception(Log.LEVEL_ERROR, e);
			System.exit(-1);
		}
		
		decoder = new Decoder();
		decoder.SetDecoderProperties(coderProps.toByteArray());
	}
	
	@Override
	public Packet[] convertFromBytes(PacketFactory packFact, byte[] realData,
			int dataLen, short clientId, byte packetIdStart, boolean splitPackets, boolean endOfWrite, boolean endOfRead) {

		ByteArrayInputStream in = new ByteArrayInputStream(realData, 0, dataLen);
		ByteArrayOutputStream out = new ByteArrayOutputStream(realData.length + 100);
		
		try {
			out.write(dataLen & 0xff);
			out.write(dataLen >> 8);
			encoder.Code(in, out, dataLen, -1, null);
			byte [] compressed = out.toByteArray();
			
			Log.get().println(Log.LEVEL_ULTRASPAM, "lzma compression: from:" + dataLen + " to:" + compressed.length);
			
			if (compressed.length < dataLen) {
				Packet [] packets = super.convertFromBytes(packFact, compressed, compressed.length, clientId, packetIdStart, false, endOfWrite, endOfRead);
				Packet lastPacket = packets[packets.length-1]; 
				lastPacket.setFlags((byte) (lastPacket.getFlags() | Packet.FLAG_COMPRESSED));
				return packets;
			} else {
				return super.convertFromBytes(packFact, realData, dataLen, clientId, packetIdStart, splitPackets, endOfWrite, endOfRead);
			}
		} catch (IOException e) {
			Log.get().println(Log.LEVEL_ERROR, "LZMA compression error.");
			Log.get().exception(Log.LEVEL_ERROR, e);
			//woa an error, this probably cant happen to us, but just in case...
			return super.convertFromBytes(packFact, realData, dataLen, clientId, packetIdStart, splitPackets, endOfWrite, endOfRead);
		}
	}

	@Override
	public byte[] convertToBytes(List<Packet> packets) {
		byte [] superBytes = super.convertToBytes(packets);
		Packet lastPacket = packets.get(packets.size() - 1);

		if (!lastPacket.isCompressed()) {
			return superBytes;
		}
		
		ByteArrayInputStream in = new ByteArrayInputStream(superBytes, 0, superBytes.length);
		//prolly no larger than 1500 bytes
		ByteArrayOutputStream out = new ByteArrayOutputStream(1500);
		
		try {
			int len = in.read() | (in.read() << 8);
			decoder.Code(in, out, len);
			return out.toByteArray();
		} catch (IOException e) {
			Log.get().exception(Log.LEVEL_ERROR, e);
			return null;
		}
	}
	
	
	
}

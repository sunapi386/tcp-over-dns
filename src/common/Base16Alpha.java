package common;

public class Base16Alpha {
	
	public final static byte [] encodeBase16(String src) {
		return encodeBase16(src.getBytes());
	}
	
	public final static byte [] encodeBase16(byte [] src) {
		byte [] encodedBytes = new byte [src.length*2];
		encodeBase16(src, 0, src.length, encodedBytes, 0);
		return encodedBytes;		
	}
		
	public final static void encodeBase16(byte [] src, int srcStart, int srcLen, byte [] dest, int destFirst) {	
		int srcLast = srcStart + srcLen;
		int destIndex = destFirst;
		for (int i=srcStart; i<srcLast; i++) {
			byte srcB = src[i];
			
			int srcNib0 = (srcB & 0xF);
			int srcNib1 = (srcB >> 4) & 0xF;
		
			dest[destIndex++] = (byte) ('a' + srcNib0);
			dest[destIndex++] = (byte) ('a' + srcNib1);
		}
	}
	
	public final static byte [] decodeBase16(String src) {
		return decodeBase16(src.getBytes());
	}
	
	public final static byte [] decodeBase16(byte [] src) {
		byte [] decodedBytes = new byte [src.length/2];
		decodeBase16(src, 0, src.length, decodedBytes, 0);
		return decodedBytes;
	}
	
	public static byte[] decodeBase16(byte[] src, int offset) {
		byte [] decodedBytes = new byte [(src.length-offset)/2];
		decodeBase16(src, offset, src.length-offset, decodedBytes, 0);
		return decodedBytes;
	}
	
	public final static void decodeBase16(byte [] src, int srcFirst, int srcLen, byte [] dest, int destFirst) {
		int srcIndex = srcFirst;
		int destLast = destFirst + srcLen/2;
		for (int i=destFirst; i<destLast; i++) {
			int srcNib0 = src[srcIndex++] - 'a';
			int srcNib1 = src[srcIndex++] - 'a';
			
			dest[i] = (byte) (srcNib0 | (srcNib1 << 4));
		}
	}


}

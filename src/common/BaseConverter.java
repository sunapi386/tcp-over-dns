package common;
import java.io.IOException;

public class BaseConverter {

	//number looks up a symbol
	private byte [] symbolSet;
	//symbol looks up a number
	private short [] inverseSymbolSet;
	
	private int byteExp;
	private int symbolExp;
	private long [] symbolPowers;
	
	//public static final Charset CHARSET = Charset.forName("ISO-8859-1");
	//public static final Charset CHARSET = Charset.forName("UTF-8");
	
	
	public static final short NOT_SET = -1;
	
	

//	/**
//	 * @param base Must be 2-256.
//	 * @param oBaseExponentiated output variable for the value of base with the returned power.
//	 * @param limit bounds the maximum value, inclusive 
//	 * @return The maximum value a long register can store before turning negative
//	 */
//	public static int findMaxExponent(int base, long [] oBaseExponentiated, long limit) {
//		if (limit <= 1) {
//			limit = Long.MAX_VALUE;
//		}
//		
//		long curVal = 1;
//		int exp = 0;
//		long nextVal = base;
//		while (nextVal > 0 && nextVal <= limit) {
//			curVal = nextVal;
//			exp++;
//			
//			nextVal *= base;
//		}
//		
//		if (oBaseExponentiated != null) {
//			oBaseExponentiated[0] = curVal;
//		}
//		return exp;
//	}
	
	/**
	 * Finds the best fit for a particular base in a signed 64bit register. 
	 */
	static void findBestExponents(int base1, int base2, int [] pExponent1, int [] pExponent2) {
		assert (base1 >= base2);
		
		if (base1 == base2) {
			pExponent1[0] = 1;
			pExponent2[0] = 1;
			return;
		}
		
		long srcVal = base1;
		long destVal = base2;

		int bestSrcExponent = 0;
		int bestDestExponent = 0;
		double bestDifference = (double)Double.MAX_VALUE;

		int srcExp = 1;
		int destExp = 1;
		//stop the loop when srcVal overflows
		outer: while (srcVal > 0) {
			while (destVal < srcVal) {
				destVal *= base2;
				destExp++;
				
				if (destVal <= 0) {
					break outer;
				}
			}
			
			double diff = (double)(destVal - srcVal) / destVal;
			if (diff <= bestDifference) {
				bestDifference = diff;
				bestSrcExponent = srcExp;
				bestDestExponent = destExp;

				//did we land perfectly?
				if (srcVal == destVal) {
					break outer; 
				}
			}

			srcVal *= base1;
			srcExp++;
		}
		assert(bestSrcExponent != 0);
		
		pExponent1[0] = bestSrcExponent;
		pExponent2[0] = bestDestExponent;
	}
	
	public BaseConverter(byte [] symbolSet) throws IllegalArgumentException {
		this.symbolSet = symbolSet;
		this.inverseSymbolSet = new short[256];
		
		java.util.Arrays.fill(this.inverseSymbolSet, NOT_SET);
		
		for (int s=0; s<this.symbolSet.length; s++) {
			int symbIndex = this.symbolSet[s] & 0xFF;
			if (inverseSymbolSet[symbIndex] != NOT_SET) {
				throw new IllegalArgumentException("Repeated characters in symbol set.");
			}
			inverseSymbolSet[symbIndex] = (short)s;
		}
		
		//find the best fitting exponents
		int numBytesSrc[] = {0};
		int numByteDest[] = {0};
		findBestExponents(256, getNumSymbols(), numBytesSrc, numByteDest);
		this.byteExp = numBytesSrc[0];
		this.symbolExp = numByteDest[0];
		
		//create the symbol power array
		this.symbolPowers = new long[this.symbolExp];
		this.symbolPowers[0] = 1;
		for (int i=1; i<this.symbolExp; i++) {
			this.symbolPowers[i] = this.symbolPowers[i-1] * getNumSymbols();
		}
	}
	
	public BaseConverter(String symbolSet) throws IllegalArgumentException {
		this(symbolSet.getBytes(/*CHARSET*/));
	}
	
	public int getNumSymbols() {
		return symbolSet.length;
	}
	
	/**
	 * Provides an upper bound on the number of bytes required to encode a string of said length.
	 * @param byteLen number of bytes in the source string
	 * @return number of bytes in the symbol string
	 */
	public int encodedSize(int byteLen) {
		return symbolExp*((byteLen + byteExp - 1) / byteExp);
	}
	
	/**
	 * Given symbolLen space, how many bytes are guaranteed to fit?
	 * @param symbolLen Number of symbols allotted
	 * @return number of bytes that can fit in symbol list
	 */
	public int decodedSize(int symbolLen) {
		return (symbolLen * byteExp) / symbolExp; 
	}
	
	/**
	 * Encodes bytes into the symbol set.
	 * @param srcData source bytes to encode
	 * @param srcStart place to begin reading bytes from
	 * @param srcLen number of bytes to encode
	 * @param destData place to write encoded symbols, use maxSizeInSymbols() to determine how much space is required.
	 * @param destStart first byte offset to begin writing encoded symbols
	 * @return number of bytes that were actually written
	 */
	int encode(byte [] srcData, int srcStart, int srcLen, byte [] destData, int destStart) {
		int destLoc = destStart;
		int srcLoc = srcStart;
		int srcEnd = srcStart + srcLen;
		
		long curValue = 0;
		//int numZeroesPending = 0;
		while (srcLoc < srcEnd) {
			
			for (int srcExp=0; srcExp < this.byteExp; srcExp++) {
				int inputValue = 0;
				if (srcLoc < srcEnd) {
					inputValue = srcData[srcLoc++] & 0xFF;
				}
				
				//forces the top bits to the important ones, saves a modulus later on
				curValue <<= 8;
				curValue |= inputValue;
			}

			for (int destExp=1; destExp <= this.symbolExp; destExp++) {
				int destSymbol = (int) (curValue / symbolPowers[symbolExp - destExp]);
				curValue = curValue - destSymbol * symbolPowers[symbolExp - destExp];
				
				//buffer the zeroes because we dont need to write them to the end
				if (destSymbol == 0) {
					//numZeroesPending++;
					destData[destLoc++] = symbolSet[0];
				} else {
					/*while (numZeroesPending > 0) {
						destData[destLoc++] = symbolSet[0];
						numZeroesPending--;
					}*/
					destData[destLoc++] = symbolSet[destSymbol];
				}
			}
			assert(curValue == 0);
		}
		
		return destLoc - destStart;
	}
	
	

	int decode(byte [] srcData, int srcStart, int srcLen, int expectedBytes, byte [] destData) throws IOException {
		try {
			int destLoc = 0;
			int destEnd = 0 + destData.length;
			int srcLoc = srcStart;
			int srcEnd = srcStart + srcLen;
			
			long curValue = 0;
	
			outer:while (destLoc < destEnd) {
				for (int destExp=1; destExp <= this.symbolExp; destExp++) {
					if (srcLoc < srcEnd) {
						int srcSymbol = inverseSymbolSet[srcData[srcLoc++]];
						if (srcSymbol == -1) {
							throw new IOException("Bad symbol received during base decode.");
						}
						curValue = curValue + srcSymbol * symbolPowers[symbolExp - destExp];
					}  else {
						break;
					}
				}
				
				for (int destExp=0; destExp < this.byteExp; destExp++) {
					if (destLoc >= destEnd) {
						break outer;
					}
					
					long valueMasked = curValue & (0xFFL << ((this.byteExp - destExp - 1)*8));
					curValue -= valueMasked;
					destData[destLoc++] = (byte) (valueMasked >> ((this.byteExp - destExp - 1)*8));
	
				}
				if (curValue != 0) {
					throw new IOException("Data corruption detected during base decode.");
				}
			}
			return srcLoc - srcStart;
		} catch (IOException ioe) {
			throw ioe;
		} catch (Exception e) {
			throw new IOException("BaseConverter decode failed");
		}
	}
	
//	public static String toBinary(byte [] bytes) {
//		String str = "";
//		for (int l : bytes) {
//			l &= 0xFF;
//			for (int i=0; i<8; i++) {
//				str += ((l>>i)&1);
//			}
//		}
//		return str;
//	}
	
}

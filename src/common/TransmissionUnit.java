package common;


public class TransmissionUnit {
	public long time;
	public byte [] data;
	public boolean splittable;
	public boolean endOfWrite;
	public boolean endOfRead;
	
	public TransmissionUnit(byte [] data, boolean splittable, boolean endOfWrite, boolean endOfRead) {
		this.time = System.currentTimeMillis();
		this.data = data;
		this.splittable = splittable;
		this.endOfWrite = endOfWrite;
		this.endOfRead = endOfRead;
	}

}
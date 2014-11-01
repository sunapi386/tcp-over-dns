package common;

import java.util.List;

public interface SlidingWindowListener {
	void onSlidingWindowCollectedPackets(List<Packet> packets);
}

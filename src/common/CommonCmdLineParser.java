package common;

import jargs.gnu.CmdLineParser;

import java.net.UnknownHostException;

public abstract class CommonCmdLineParser extends CmdLineParser {
	private Option domain;
	private Option mtu;
	private Option logLevel;
	private Option logFile;

	public CommonCmdLineParser() {
		domain = addStringOption("domain");
		mtu = addIntegerOption("mtu");
		logLevel = addIntegerOption("log-level");
		logFile = addStringOption("log-file");
	}

	public CommonOptions parseCmdLine(String args[]) {
		try {
			parse(args);
			
			CommonOptions result = new CommonOptions();
			
			result.domain = (String) getOptionValue(domain);
			Integer mtuVal = (Integer)getOptionValue(mtu);
			Integer logLevelVal = (Integer)getOptionValue(logLevel);
			String logFileVal = (String) getOptionValue(logFile);
			
			if (mtuVal != null) {
				result.mtu = mtuVal;
			}

			if (logLevelVal != null) {
				result.logLevel = logLevelVal;
			} else {
				result.logLevel = -1;
			}
			
			if (logFileVal != null) {
				result.logFile = logFileVal;
			}
		
			if (!setDefaults(result)) {
				return null;
			}
			
			if (result.domain == null) {
				return null;
			}
			
			return result;
		} catch (IllegalOptionValueException e1) {
			e1.printStackTrace();
			return null;
		} catch (UnknownOptionException e1) {
			e1.printStackTrace();
			return null;
		} catch (UnknownHostException e1) {
			e1.printStackTrace();
			return null;
		}
	}

	private static boolean setDefaults(CommonOptions result) throws UnknownHostException {
		if (result.mtu <= 0) {
			result.mtu = Common.DEFAULT_MTU;
		}
		
		if (result.logLevel < 0) {
			result.logLevel = Common.DEFAULT_LOGLEVEL;
		}
		return true;
	}

	public abstract void printUsage();
}

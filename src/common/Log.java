package common;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DecimalFormat;

public class Log {
	private static Log singleton;
	
	public static void set(Log log) {
		singleton = log;
	}

	public static Log get() {
		return singleton;
	}

	
	public static int LEVEL_ERROR     = (1 << 0);
	public static int LEVEL_WARN      = (1 << 1);
	public static int LEVEL_INFO      = (1 << 2);
	public static int LEVEL_SPAM      = (1 << 3);
	public static int LEVEL_ULTRASPAM = (1 << 4);
	
	private PrintStream outputStream;
	private int levelFlags;
	private long timeCreated;
	private DecimalFormat timeFormatter;
	private boolean frontOfLine;
	
	private static final double msToSec = 1.0/1000;
	
	/**
	 * Constructs a log with the specified log file.
	 * @param logFile The file to append to, if null, then we write to stdout.
	 * @param logLevelFlags Which levels to log.
	 * @throws FileNotFoundException Occurs if the log file is unwritable.
	 */
	public Log(String logFile, int logLevelFlags) throws FileNotFoundException {
		this(openLogFile(logFile), logLevelFlags);
	}
	
	public Log(PrintStream out, int logLevelFlags) {
		this.outputStream = out;
		this.levelFlags = logLevelFlags;
		this.timeCreated = System.currentTimeMillis();
		this.timeFormatter = new DecimalFormat("000000.0");
		this.frontOfLine = true;
	}
	
	private static PrintStream openLogFile(String logFile) throws FileNotFoundException {
		if (logFile == null) {
			return System.out;
		}
		
		File file = new File(logFile);
		return new PrintStream(new FileOutputStream(file, true));
	}

	private String time() {
		long timeDiff = System.currentTimeMillis() - timeCreated;
		return timeFormatter.format(timeDiff * msToSec);
	}
	
	public void println(int level, Object line) {
		if ((levelFlags & level) != 0) {
			if (frontOfLine) {
				outputStream.println(time() + " " + Thread.currentThread().getName() + ": " + line);
			} else {
				outputStream.println(line);
			}
		}
		frontOfLine = true;
	}
	
	public void print(int level, Object print) {
		if ((levelFlags & level) != 0) {
			if (frontOfLine) {
				outputStream.print(time() + " " + Thread.currentThread().getName() + ": " + print);
			} else {
				outputStream.print(print);
			}
		}
		frontOfLine = false;
	}
	
	public void exception(int level, Throwable t) {
		if ((levelFlags & level) != 0) {
			if (frontOfLine) {
				outputStream.print(time() + " " + Thread.currentThread().getName() + ": ");
			}
			t.printStackTrace(outputStream);
			outputStream.flush();
		}
		frontOfLine = false;
	}

	public int getLevelFlags() {
		return levelFlags;
	}

	public void setLevelFlags(int levelFlags) {
		this.levelFlags = levelFlags;
	}
	
	public static int integerToLevels(int i) {
		int level = 0;
		while (i > 0) {
			level |= (1 << (i - 1));
			i--;
		}
		return level;
	}
}

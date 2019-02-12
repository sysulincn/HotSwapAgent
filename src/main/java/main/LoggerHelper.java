package main;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

class LoggerHelper {

	private static class MyFormatter extends Formatter {
		@Override
		public String format(LogRecord record) {
			StringBuilder sb = new StringBuilder();
			sb.append(Instant.ofEpochMilli(record.getMillis()).atZone(ZoneId.systemDefault()).toLocalDateTime().format(DATE_TIME_FORMATTER)).append(" ");
			sb.append(record.getLevel()).append(":");
			sb.append(record.getMessage());
			if (record.getThrown() != null) {
				sb.append("\t").append(getStackTrace(record.getThrown()));
			}
			sb.append("\n");
			return sb.toString();
		}
	}

	static final String pattern = "yyyy-MM-dd HH:mm:ss";
	static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(pattern);
	private static Logger logger = null;

	static void init(Class initClass, String logPath, String logFileName) throws Exception {
		if (logger == null) {
			logger = Logger.getLogger(initClass.getName());
			logger.setUseParentHandlers(false);
			Path path = Files.createDirectories(Paths.get(logPath)).resolve(logFileName);
			Handler handler = new FileHandler(path.toAbsolutePath().toString(), true);
			handler.setFormatter(new MyFormatter());
			logger.addHandler(handler);
			handler = new ConsoleHandler();
			handler.setFormatter(new MyFormatter());
			handler.setLevel(Level.ALL);
			logger.addHandler(handler);
		}
	}

	static Logger getLogger() {
		return logger;
	}

	static void closeLogger() {
		if (logger == null) {
			return;
		}
		for (Handler handler : logger.getHandlers()) {
			handler.close();
		}
		logger = null;
	}

	private static String getStackTrace(Throwable t) {
		StringWriter stringWriter = new StringWriter();
		PrintWriter printWriter = new PrintWriter(stringWriter);
		t.printStackTrace(printWriter);
		return stringWriter.toString().replaceAll("\n", "\n\t");
	}

	static long getTimeFromLog(String log) {
		return LocalDateTime.parse(log.substring(0, pattern.length()), DATE_TIME_FORMATTER).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}
}

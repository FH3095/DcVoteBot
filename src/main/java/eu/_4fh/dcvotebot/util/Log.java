package eu._4fh.dcvotebot.util;

import java.util.logging.Logger;

public class Log {
	private Log() {
	}

	public static Logger getLog(final Object obj) {
		final Class<?> clazz;
		if (obj instanceof Class<?>) {
			clazz = (Class<?>) obj;
		} else {
			clazz = obj.getClass();
		}
		return Logger.getLogger(clazz.getName());
	}
}

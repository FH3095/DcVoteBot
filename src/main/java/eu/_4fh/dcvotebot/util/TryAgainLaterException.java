package eu._4fh.dcvotebot.util;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

@DefaultAnnotation(NonNull.class)
public class TryAgainLaterException extends RuntimeException {
	private static final long serialVersionUID = -1773143998318503501L;

	public TryAgainLaterException(String msg) {
		super(msg);
	}

	public TryAgainLaterException(String msg, Throwable cause) {
		super(msg, cause);
	}
}

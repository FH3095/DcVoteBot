package eu._4fh.dcvotebot.discord;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class VoteUpdateHandler implements AutoCloseable {
	private final ScheduledExecutorService executorService = new ScheduledThreadPoolExecutor(1);

	@Override
	public void close() throws Exception {
		executorService.shutdown();
	}
}

package eu._4fh.dcvotebot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.dcvotebot.discord.Bot;
import eu._4fh.dcvotebot.util.Config;
import eu._4fh.dcvotebot.util.Log;

@DefaultAnnotation(NonNull.class)
public class Main {
	private static final class LoggingConfigUpdater implements Runnable {
		@Override
		public void run() {
			try {
				TimeUnit.SECONDS.sleep(60);
				LogManager.getLogManager().updateConfiguration(null);
			} catch (Throwable t) { // NOSONAR This thread shouldn't end
				t.printStackTrace(System.err); // NOSONAR We just tried to update the logging configuration
			}
		}
	}

	private static final Semaphore shutdown = new Semaphore(0);

	private static final class InputChecker implements Runnable {
		@Override
		public void run() {
			try {
				final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
				String line;
				do {
					line = reader.readLine().trim();
					break; // For now shutdown on any line. Need to change for sharding.
				} while (!"stop".equals(line));
				shutdown.release();
			} catch (IOException e) {
				Log.getLog(this).log(Level.WARNING, "Cant read from stdin", e);
			}
		}
	}

	public static void main(String[] args) {
		try {
			LogManager.getLogManager().readConfiguration();
			LogManager.getLogManager().updateConfiguration(null); // Just to test that this works, try to update logging config
		} catch (SecurityException | IOException e) {
			throw new RuntimeException(e);
		}

		final Thread loggingConfigUpdaterThread = new Thread(new LoggingConfigUpdater(), "LoggingConfigUpdater");
		loggingConfigUpdaterThread.setDaemon(true);
		loggingConfigUpdaterThread.start();

		final Thread inputCheckerThread = new Thread(new InputChecker(), "InputChecker");
		inputCheckerThread.setDaemon(true);
		inputCheckerThread.start();
		Runtime.getRuntime().addShutdownHook(new Thread(shutdown::release));

		new Main().run(args);
	}

	private void run(String[] args) {
		final int shardTotal = Config.instance().discordShards;
		final int shardId;
		if (shardTotal <= 1) {
			shardId = 0;
		} else {
			throw new IllegalStateException("Multiple shards are currently not supported");
		}

		try (final Bot bot = new Bot(shardId, shardTotal)) {
			shutdown.acquireUninterruptibly();
			Log.getLog(this).info("Initiating shutdown");
		}
	}
}

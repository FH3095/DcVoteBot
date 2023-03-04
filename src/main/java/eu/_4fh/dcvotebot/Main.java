package eu._4fh.dcvotebot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.dcvotebot.discord.Bot;
import eu._4fh.dcvotebot.util.Config;

@DefaultAnnotation(NonNull.class)
public class Main {
	public static void main(String[] args) {
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
			final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			String line;
			do {
				line = reader.readLine().trim();
				break; // For now shutdown on any line. Need to change for sharding.
			} while (!"stop".equals(line));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}

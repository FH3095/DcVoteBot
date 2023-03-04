package eu._4fh.dcvotebot.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.apache.commons.lang3.Validate;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

@DefaultAnnotation(NonNull.class)
public class Config {
	private static final Config instance = new Config();

	public static Config instance() {
		return instance;
	}

	public final int dbLockWaitSeconds;
	public final Path dbDataDir;
	public final int discordShards;
	public final String discordToken;
	public final int interactionTimeout;

	private Config() {
		final String configFile = System.getProperty("DcVoteBotConfig", "main.cfg");
		final Properties props = readFile(configFile);
		dbLockWaitSeconds = Integer.parseInt(nonNull(props, "db.lockWaitTimeout"));
		dbDataDir = Path.of(nonNull(props, "db.dataDir")).normalize().toAbsolutePath();
		discordShards = Integer.parseInt(nonNull(props, "discord.shards"));
		Validate.inclusiveBetween(1, Integer.MAX_VALUE, discordShards, "Config-Value discord.shards must be >= 1");
		discordToken = nonNull(props, "discord.token");
		interactionTimeout = Integer.parseInt(nonNull(props, "interaction.timeoutMinutes"));
		Validate.inclusiveBetween(1, TimeUnit.DAYS.toMinutes(1), interactionTimeout,
				"interaction.timeoutMinutes must be >= 1 and less than one day");
	}

	private Properties readFile(final String configFile) {
		final Properties properties = new Properties();
		try (final Reader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8))) {
			properties.load(reader);
		} catch (IOException e) {
			Log.getLog(this).log(Level.SEVERE, "Cant read " + configFile, e);
			throw new RuntimeException("Cant read " + configFile, e);
		}
		return properties;
	}

	private String nonNull(final Properties properties, final String propertyName) {
		final String result = properties.getProperty(propertyName);
		if (result == null || result.isBlank()) {
			throw new IllegalStateException("Missing config setting " + propertyName);
		}
		return result.trim();
	}
}

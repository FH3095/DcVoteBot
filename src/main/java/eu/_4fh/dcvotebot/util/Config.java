package eu._4fh.dcvotebot.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;

public class Config {
	private static Config instance;

	public static Config instance() {
		if (instance == null) {
			instance = new Config();
		}
		return instance;
	}

	public final int dbLockWaitSeconds;
	public final Path dbDataDir;

	private Config() {
		final String configFile = System.getProperty("DcVoteBotConfig", "main.cfg");
		final Properties props = readFile(configFile);
		dbLockWaitSeconds = Integer.parseInt(nonNull(props, "db.lockWaitTimeout"));
		dbDataDir = Path.of(nonNull(props, "db.dataDir")).normalize().toAbsolutePath();
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

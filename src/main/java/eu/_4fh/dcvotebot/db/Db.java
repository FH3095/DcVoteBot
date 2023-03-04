package eu._4fh.dcvotebot.db;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;
import org.json.JSONObject;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.dcvotebot.util.Config;
import eu._4fh.dcvotebot.util.Log;
import eu._4fh.dcvotebot.util.TryAgainLaterException;

@DefaultAnnotation(NonNull.class)
public class Db {
	public class LockHolder implements AutoCloseable {
		private final long serverId;
		private final Throwable startException;
		private @CheckForNull ReentrantLock lock;

		private LockHolder(final long serverId) {
			this.serverId = serverId;
			this.startException = new RuntimeException().fillInStackTrace();
			this.lock = null;

			final ReentrantLock lock = serverLocks.computeIfAbsent(serverId, s -> new ReentrantLock(true));
			try {
				if (!lock.tryLock(lockWaitSeconds, TimeUnit.SECONDS)) {
					throw new TryAgainLaterException("Get lock timed-out for server " + serverId);
				}
			} catch (InterruptedException e) {
				throw new TryAgainLaterException("Cant get lock for server " + serverId, e);
			}
			this.lock = lock;
		}

		@Override
		protected void finalize() throws Throwable {
			if (lock != null) {
				Log.getLog(this).log(Level.SEVERE, "Finalized LockHolder, stacktrace at start", startException);
				close();
			}
		}

		@Override
		public void close() {
			if (lock != null) {
				lock.unlock();
				lock = null;
			}
		}
	}

	private static final Db instance = new Db();

	public static Db instance() {
		return instance;
	}

	private Db() {
	}

	private static final String FILE_ENDING = ".json";

	private final Map<Long, ReentrantLock> serverLocks = new ConcurrentHashMap<>();
	private final int lockWaitSeconds = Config.instance().dbLockWaitSeconds;
	private final Path dataDir = Config.instance().dbDataDir;

	private final LoadingCache<Long, VoteSettings> defaultSettingsCache = buildCache(this::loadDefaultSettings);
	private final LoadingCache<Pair<Long, Long>, Vote> votesCache = buildCache(this::loadVote);
	private final LoadingCache<Long, Collection<Long>> allServerVoteIdsCache = buildCache(this::loadAllServerVoteIds);

	private <K, V> LoadingCache<K, V> buildCache(CacheLoader<K, V> loader) {
		return Caffeine.newBuilder().expireAfterWrite(Duration.ofDays(1)).softValues()
				.scheduler(Scheduler.systemScheduler()).maximumSize(1_000_000).build(loader);
	}

	private static String getServerFile(final long serverId) {
		return "server_" + Long.toUnsignedString(serverId) + FILE_ENDING;
	}

	private static String getVoteFile(final long serverId, final long voteId) {
		return "server_" + Long.toUnsignedString(serverId) + "_vote_" + Long.toUnsignedString(voteId) + FILE_ENDING;
	}

	private VoteSettings loadDefaultSettings(final long serverId) throws IOException {
		final Path serverFile = dataDir.resolve(getServerFile(serverId));
		if (!Files.isReadable(serverFile)) {
			return VoteSettings.getDefault();
		}
		final String data = Files.readString(serverFile, StandardCharsets.UTF_8);
		return VoteSettings.fromJson(new JSONObject(data));
	}

	private Vote loadVote(final Pair<Long, Long> serverAndMsgId) throws IOException {
		final String data = Files.readString(
				dataDir.resolve(getVoteFile(serverAndMsgId.getLeft(), serverAndMsgId.getRight())),
				StandardCharsets.UTF_8);
		return Vote.fromJson(new JSONObject(data));
	}

	private List<Long> loadAllServerVoteIds(final long serverId) throws IOException {
		final Pattern voteFilePattern = Pattern
				.compile("^server_" + Long.toUnsignedString(serverId) + "_vote_(\\d+)\\.json$");
		final List<Long> result = new ArrayList<>();
		try (final DirectoryStream<Path> files = Files.newDirectoryStream(dataDir)) {
			files.forEach(file -> {
				final Matcher match = voteFilePattern.matcher(file.getFileName().toString());
				if (match.matches()) {
					result.add(Long.parseUnsignedLong(match.group(1)));
				}
			});
		}
		return Collections.unmodifiableList(result);
	}

	public LockHolder getLock(final long serverId) {
		return new LockHolder(serverId);
	}

	private void writeJson(final String file, final JSONObject obj) throws IOException {
		try (final OutputStream os = Files.newOutputStream(dataDir.resolve(file), StandardOpenOption.WRITE,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
				final Writer out = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
			obj.write(out, 2, 0);
		}
	}

	public VoteSettings getDefaultSettings(final LockHolder lock) {
		return defaultSettingsCache.get(lock.serverId);
	}

	public void setDefaultSettings(final LockHolder lock, final VoteSettings settings) {
		final String file = getServerFile(lock.serverId);
		try {
			writeJson(file, settings.toJson());
		} catch (IOException e) {
			throw new RuntimeException("Cant write " + file, e);
		}
		defaultSettingsCache.invalidate(lock.serverId);
	}

	public @CheckForNull Vote getVote(final LockHolder lock, final long voteMsgId) {
		Validate.notNull(lock);
		return votesCache.get(Pair.of(lock.serverId, voteMsgId));
	}

	public void setVote(final LockHolder lock, final long voteMsgId, final Vote vote) {
		Validate.notNull(lock);
		final String file = getVoteFile(lock.serverId, voteMsgId);
		try {
			writeJson(file, vote.toJson());
		} catch (IOException e) {
			throw new RuntimeException("Cant write " + file, e);
		}
		votesCache.invalidate(Pair.of(lock.serverId, voteMsgId));
		allServerVoteIdsCache.invalidate(lock.serverId);
	}

	public Collection<Long> getAllServerVoteIds(final LockHolder lock) {
		Validate.notNull(lock);
		return allServerVoteIdsCache.get(lock.serverId);
	}

	/*package for test*/ void forTestResetCaches() {
		defaultSettingsCache.invalidateAll();
		votesCache.invalidateAll();
		allServerVoteIdsCache.invalidateAll();
	}
}

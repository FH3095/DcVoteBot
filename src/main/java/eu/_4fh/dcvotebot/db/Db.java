package eu._4fh.dcvotebot.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import javax.sql.DataSource;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.tuple.Pair;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import eu._4fh.dcvotebot.util.Config;
import eu._4fh.dcvotebot.util.Log;

@DefaultAnnotation(NonNull.class)
public class Db {
	public static class NotFoundException extends RuntimeException {
		private static final long serialVersionUID = -3872395108235013640L;

		public NotFoundException(String msg) {
			super(msg);
		}
	}

	public class Transaction implements AutoCloseable {
		private @Nullable Connection con;
		private final long serverId;
		private final Throwable startException;

		private Transaction(final long serverId) {
			this.serverId = serverId;
			this.startException = new RuntimeException().fillInStackTrace();
			try {
				this.con = dataSource.getConnection();
			} catch (SQLException e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		protected void finalize() { // NOSONAR
			if (con != null) {
				Log.getLog(this).log(Level.SEVERE, "Finalized LockHolder, stacktrace at start", startException);
				close();
			}
		}

		@Override
		public void close() {
			if (con != null) {
				try {
					con.rollback();
					con.close();
				} catch (SQLException e) {
					Log.getLog(this).log(Level.SEVERE, "Cant close jdbc connection", e);
				}
				con = null;
			}
		}
	}

	private static Db instance = new Db(null);

	@SuppressFBWarnings(value = "MS_EXPOSE_REP")
	public static Db instance() {
		return instance;
	}

	private static int testDbCounter = 1;

	@SuppressFBWarnings(value = "MS_EXPOSE_REP")
	public static Db forTestNewDb() {
		final HikariDataSource hikariDs = Config.instance().dataSource;
		final HikariConfig config = new HikariConfig();
		hikariDs.copyStateTo(config);
		config.setJdbcUrl(config.getJdbcUrl().replace("testdb", "testdb" + testDbCounter++));
		instance = new Db(new HikariDataSource(config));
		return instance;
	}

	private Db(final @CheckForNull DataSource testDataSource) {
		if (testDataSource != null) {
			this.dataSource = testDataSource;
		} else {
			this.dataSource = Config.instance().dataSource;
		}
	}

	private final DataSource dataSource;

	private final Cache<Long, VoteSettings> defaultSettingsCache = buildCache();
	private final Cache<Pair<Long, Long>, Vote> votesCache = buildCache();

	private <K, V> Cache<K, V> buildCache() {
		return Caffeine.newBuilder().expireAfterWrite(Duration.ofDays(1)).softValues()
				.scheduler(Scheduler.systemScheduler()).maximumSize(1_000_000).build();
	}

	private <T> T logAndThrow(final String msg, final SQLException e) {
		Log.getLog(this).log(Level.SEVERE, msg, e);
		throw new IllegalStateException(e);
	}

	private VoteSettings loadDefaultSettings(final Transaction trans) {
		try (PreparedStatement stmt = trans.con.prepareStatement(
				"SELECT votesPerVoter, durationSeconds, voterCanChangeVotes, timezoneId FROM default_settings WHERE serverId = ?")) {
			stmt.setLong(1, trans.serverId);
			try (final ResultSet rs = stmt.executeQuery()) {
				if (!rs.next()) {
					return VoteSettings.getDefault();
				}
				return createVoteSettings(rs.getString(4), rs, 1);
			}
		} catch (SQLException e) {
			return logAndThrow("Cant get default-settings from db", e);
		}
	}

	private VoteSettings createVoteSettings(final String timezoneId, final ResultSet rs, int i) throws SQLException {
		return new VoteSettings(rs.getByte(i++), rs.getLong(i++), rs.getBoolean(i++), timezoneId); // NOSONAR
	}

	private Vote loadVote(final Transaction trans, final long messageId) {
		try (PreparedStatement voteStmt = trans.con.prepareStatement(
				"SELECT channelId, title, description, start, votesPerVoter, durationSeconds, voterCanChangeVotes FROM votes WHERE messageId = ? AND serverId = ?");
				PreparedStatement voteOptionsStmt = trans.con
						.prepareStatement("SELECT id, title FROM vote_options WHERE messageId = ? ORDER BY id");
				PreparedStatement votersVotesStmt = trans.con.prepareStatement(
						"SELECT voteOptionId, voterId FROM voters_votes WHERE voteOptionId IN (SELECT id FROM vote_options WHERE messageId = ?) ORDER BY voteOptionId")) {
			voteStmt.setLong(1, messageId);
			voteStmt.setLong(2, trans.serverId);
			voteOptionsStmt.setLong(1, messageId);
			votersVotesStmt.setLong(1, messageId);
			try (ResultSet voteRs = voteStmt.executeQuery()) {
				if (!voteRs.next()) {
					throw new NotFoundException("Unknown vote " + messageId + " for server " + trans.serverId);
				}

				final Map<Long, Set<Long>> votersVotes = new HashMap<>();
				try (ResultSet votersVotesRs = votersVotesStmt.executeQuery()) {
					while (votersVotesRs.next()) {
						votersVotes.computeIfAbsent(votersVotesRs.getLong(1), optionId -> new HashSet<>())
								.add(votersVotesRs.getLong(2));
					}
				}

				final List<VoteOption> options = new ArrayList<>();
				try (ResultSet voteOptionsRs = voteOptionsStmt.executeQuery()) {
					while (voteOptionsRs.next()) {
						final long voteOptionId = voteOptionsRs.getLong(1);
						final VoteOption option = new VoteOption(voteOptionId, voteOptionsRs.getString(2), Collections
								.unmodifiableSet(votersVotes.getOrDefault(voteOptionId, Collections.emptySet())));
						options.add(option);
					}
				}

				final VoteSettings settings = createVoteSettings(getDefaultSettings(trans).timezoneId, voteRs, 5);

				return new Vote(settings, voteRs.getLong(1), voteRs.getString(2), voteRs.getString(3),
						Instant.ofEpochSecond(voteRs.getLong(4)), Collections.unmodifiableList(options));
			}
		} catch (SQLException e) {
			return logAndThrow("Cant get vote from db", e);
		}
	}

	public Transaction getTransaction(final long serverId) {
		return new Transaction(serverId);
	}

	public VoteSettings getDefaultSettings(final Transaction trans) {
		return defaultSettingsCache.get(trans.serverId, serverId -> loadDefaultSettings(trans));
	}

	public void setDefaultSettings(final Transaction trans, final VoteSettings settings) {
		try (PreparedStatement existsStmt = trans.con
				.prepareStatement("SELECT 1 FROM default_settings WHERE serverId = ?")) {
			existsStmt.setLong(1, trans.serverId);
			try (ResultSet existsRs = existsStmt.executeQuery()) {
				final String query;
				if (existsRs.next()) {
					query = "UPDATE default_settings SET votesPerVoter = ?, durationSeconds = ?, voterCanChangeVotes = ?, timezoneId = ? WHERE serverId = ?";
				} else {
					query = "INSERT INTO default_settings(votesPerVoter, durationSeconds, voterCanChangeVotes, timezoneId, serverId) VALUES(?,?,?,?,?)";
				}
				try (PreparedStatement stmt = trans.con.prepareStatement(query)) {
					stmt.setByte(1, settings.answersPerUser);
					stmt.setLong(2, settings.duration.toSeconds());
					stmt.setBoolean(3, settings.canChangeAnswers);
					stmt.setString(4, settings.timezoneId);
					stmt.setLong(5, trans.serverId);
					Validate.inclusiveBetween(1, 1, stmt.executeUpdate());
					trans.con.commit();
				}
			}
		} catch (SQLException e) {
			logAndThrow("Cant update settings", e);
		}
		defaultSettingsCache.invalidate(trans.serverId);
	}

	public Vote getVote(final Transaction trans, final long voteMsgId) {
		Validate.notNull(trans);
		return votesCache.get(Pair.of(trans.serverId, voteMsgId), idPair -> loadVote(trans, voteMsgId));
	}

	public void insertVote(final Transaction trans, final long voteMsgId, final Vote vote) {
		Validate.isTrue(voteMsgId != 0);

		try (PreparedStatement voteStmt = trans.con.prepareStatement(
				"INSERT INTO votes(serverId, messageId, channelId, title, description, start, votesPerVoter, durationSeconds, voterCanChangeVotes) VALUES (?,?,?,?,?,?,?,?,?)");
				PreparedStatement voteOptionsStmt = trans.con
						.prepareStatement("INSERT INTO vote_options(messageId, title) VALUES (?,?)")) {
			voteStmt.setLong(1, trans.serverId);
			voteStmt.setLong(2, voteMsgId);
			voteStmt.setLong(3, vote.channelId);
			voteStmt.setString(4, vote.title);
			voteStmt.setString(5, vote.description);
			voteStmt.setLong(6, vote.start.getEpochSecond());
			voteStmt.setByte(7, vote.settings.answersPerUser);
			voteStmt.setLong(8, vote.settings.duration.toSeconds());
			voteStmt.setBoolean(9, vote.settings.canChangeAnswers);
			Validate.inclusiveBetween(1, 1, voteStmt.executeUpdate());
			for (final VoteOption option : vote.options) {
				voteOptionsStmt.setLong(1, voteMsgId);
				voteOptionsStmt.setString(2, option.name);
				Validate.inclusiveBetween(1, 1, voteOptionsStmt.executeUpdate());
			}
			trans.con.commit();
		} catch (SQLException e) {
			logAndThrow("Cant insert vote", e);
		}
		votesCache.invalidate(Pair.of(trans.serverId, voteMsgId));
	}

	public void updateVote(final Transaction trans, final long voteMsgId, final Vote vote) {
		Validate.isTrue(voteMsgId != 0);

		try (PreparedStatement stmt = trans.con.prepareStatement(
				"UPDATE votes SET title = ?, description = ?, votesPerVoter = ?, durationSeconds = ?, voterCanChangeVotes = ? WHERE messageId = ?")) {
			stmt.setString(1, vote.title);
			stmt.setString(2, vote.description);
			stmt.setByte(3, vote.settings.answersPerUser);
			stmt.setLong(4, vote.settings.duration.toSeconds());
			stmt.setBoolean(5, vote.settings.canChangeAnswers);
			stmt.setLong(6, voteMsgId);
			Validate.inclusiveBetween(1, 1, stmt.executeUpdate());
			trans.con.commit();
		} catch (SQLException e) {
			logAndThrow("Cant update vote", e);
		}
		votesCache.invalidate(Pair.of(trans.serverId, voteMsgId));
	}

	public void updateVoteVotes(final Transaction trans, final long voterId, final long voteMsgId,
			final Set<Long> votes) {
		try (PreparedStatement delStmt = trans.con.prepareStatement("DELETE FROM voters_votes WHERE voterId = ? AND "
				+ "voteOptionId IN (SELECT id FROM vote_options WHERE messageId = ?)");
				PreparedStatement insertStmt = trans.con
						.prepareStatement("INSERT INTO voters_votes(voteOptionId, voterId) VALUES (?,?)")) {
			delStmt.setLong(1, voterId);
			delStmt.setLong(2, voteMsgId);
			delStmt.executeUpdate();

			for (final long voteOptionId : votes) {
				insertStmt.setLong(1, voteOptionId);
				insertStmt.setLong(2, voterId);
				Validate.inclusiveBetween(1, 1, insertStmt.executeUpdate());
			}
			trans.con.commit();
		} catch (SQLException e) {
			logAndThrow("Cant update votes", e);
		}
		votesCache.invalidate(Pair.of(trans.serverId, voteMsgId));
	}

	public Collection<Long> getAllServerVoteIds(final Transaction trans) {
		Validate.notNull(trans);
		try (final PreparedStatement stmt = trans.con
				.prepareStatement("SELECT messageId FROM votes WHERE serverId = ?")) {
			stmt.setLong(1, trans.serverId);
			final Set<Long> ids = new HashSet<>();
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					ids.add(rs.getLong(1));
				}
			}
			return Collections.unmodifiableSet(ids);
		} catch (SQLException e) {
			return logAndThrow("Cant get all vote-ids for server " + trans.serverId, e);
		}
	}

	public void saveToUpdateVotes(final Collection<Long> messageIds) {
		try (Connection con = dataSource.getConnection();
				PreparedStatement stmt = con.prepareStatement("INSERT INTO to_update_votes(messageId) VALUES (?)")) {
			for (final long messageId : messageIds) {
				stmt.setLong(1, messageId);
				stmt.addBatch();
			}
			stmt.executeBatch();
			con.commit();
		} catch (SQLException e) {
			logAndThrow("Cant save to update votes", e);
		}
	}

	public Collection<Pair<Long, Long>> loadToUpdateVotes() {
		try (Connection con = dataSource.getConnection();
				Statement stmt = con.createStatement();
				ResultSet rs = stmt.executeQuery(
						"SELECT serverId, messageId FROM votes WHERE messageId IN (SELECT messageId FROM to_update_votes)")) {
			final List<Pair<Long, Long>> result = new ArrayList<>();
			while (rs.next()) {
				result.add(Pair.of(rs.getLong(1), rs.getLong(2)));
			}
			return result;
		} catch (SQLException e) {
			return logAndThrow("Cant load to update votes", e);
		}
	}

	/*package for test*/ void forTestResetCaches() {
		defaultSettingsCache.invalidateAll();
		votesCache.invalidateAll();
	}
}

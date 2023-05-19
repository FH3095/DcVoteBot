package eu._4fh.dcvotebot.discord;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.dcvotebot.db.Db;
import eu._4fh.dcvotebot.db.Db.NotFoundException;
import eu._4fh.dcvotebot.db.Db.Transaction;
import eu._4fh.dcvotebot.db.Vote;
import eu._4fh.dcvotebot.util.Config;
import eu._4fh.dcvotebot.util.Log;
import net.dv8tion.jda.api.events.guild.GuildAvailableEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.GuildTimeoutEvent;
import net.dv8tion.jda.api.events.guild.GuildUnavailableEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

@DefaultAnnotation(NonNull.class)
public class VoteUpdateHandler extends ListenerAdapter implements AutoCloseable {
	private static class TodoElement {
		public final long serverId;
		public final long messageId;
		public volatile Instant timeout; // NOSONAR Private class anyway
		public volatile Instant nextTry; // NOSONAR Private class anyway
		public AtomicInteger tries; // NOSONAR Private class anyway

		private TodoElement(final long serverId, final long messageId, final Instant timeout) {
			this.serverId = serverId;
			this.messageId = messageId;
			this.timeout = timeout;
			nextTry = Instant.now();
			tries = new AtomicInteger(0);
		}
	}

	private final Duration tryIntervall;
	private final int maxTries;
	private final Bot bot;
	private final Db db;
	private final ScheduledExecutorService executorService;
	private final Map<Long, TodoElement> todoQueue;
	private final Set<Long> availableGuilds;
	private final Duration updateTimeout;
	private final Duration deleteVotesOffsetDays;

	public VoteUpdateHandler(Bot bot) {
		this.bot = bot;
		updateTimeout = Duration.ofMinutes(Config.instance().updateVotesTimeoutMinutes);
		this.db = Db.instance();
		this.todoQueue = new LinkedHashMap<>();
		this.availableGuilds = ConcurrentHashMap.newKeySet();
		this.executorService = new ScheduledThreadPoolExecutor(1);
		this.tryIntervall = Duration.ofSeconds(Config.instance().updateRetryPause);
		this.maxTries = Config.instance().updateMaxTries;
		this.deleteVotesOffsetDays = Duration.ofDays(Config.instance().deleteVotesOffsetDays);
	}

	@Override
	public void onGuildReady(GuildReadyEvent event) {
		final long guildId = event.getGuild().getIdLong();
		availableGuilds.add(guildId);
		loadTodo(guildId);
	}

	@Override
	public void onGuildJoin(GuildJoinEvent event) {
		final long guildId = event.getGuild().getIdLong();
		availableGuilds.add(guildId);
		loadTodo(guildId);
	}

	@Override
	public void onGuildAvailable(GuildAvailableEvent event) {
		final long guildId = event.getGuild().getIdLong();
		availableGuilds.add(guildId);
		Log.getLog(this).warning("Guild " + event.getGuild().getName() + "(" + guildId + ") is now available again");
		loadTodo(guildId);
	}

	@Override
	public void onGuildUnavailable(GuildUnavailableEvent event) {
		availableGuilds.remove(event.getGuild().getIdLong());
		Log.getLog(this).warning(
				"Guild " + event.getGuild().getName() + "(" + event.getGuild().getId() + ") is now unavailable");
	}

	@Override
	public void onGuildTimeout(GuildTimeoutEvent event) {
		availableGuilds.remove(event.getGuildIdLong());
		Log.getLog(this).warning("Guild " + event.getGuildIdLong() + " timed out");
	}

	@Override
	public void onGuildLeave(GuildLeaveEvent event) {
		availableGuilds.remove(event.getGuild().getIdLong());
	}

	private void saveTodo() {
		final List<Pair<Long, Long>> messageIds;
		synchronized (todoQueue) {
			messageIds = todoQueue.values().stream().map(e -> Pair.of(e.serverId, e.messageId))
					.collect(Collectors.toUnmodifiableList());
		}
		db.saveToUpdateVotes(messageIds);
	}

	private void loadTodo(final long guildId) {
		final Collection<Pair<Long, Long>> todos = db.loadAndDeleteToUpdateVotes(guildId);
		todos.forEach(
				serverAndMessageId -> addToUpdateVote(serverAndMessageId.getLeft(), serverAndMessageId.getRight()));
	}

	public void addToUpdateVote(final long serverId, final long messageId) {
		final Instant timeout = Instant.now().plus(updateTimeout);
		synchronized (todoQueue) {
			todoQueue.compute(messageId, (key, value) -> {
				if (value == null) {
					return new TodoElement(serverId, messageId, timeout);
				} else {
					value.timeout = timeout;
					return value;
				}
			});
		}
	}

	public void start() {
		final long intervall = Config.instance().updateIntervalMilliseconds;
		executorService.scheduleWithFixedDelay(this::updateVote, intervall, intervall, TimeUnit.MILLISECONDS);
		executorService.scheduleWithFixedDelay(this::searchForVotesToEditLast, 10, 10, TimeUnit.SECONDS);
		executorService.scheduleWithFixedDelay(this::deleteOldVotes, 3, 3, TimeUnit.HOURS);
	}

	private @CheckForNull TodoElement getNextElement() {
		synchronized (todoQueue) {
			final Instant now = Instant.now();
			final Map<Long, TodoElement> bufferedElements = new LinkedHashMap<>();
			@CheckForNull
			TodoElement element = null;
			while (element == null && !todoQueue.isEmpty()) {
				final Iterator<TodoElement> it = todoQueue.values().iterator();
				element = it.next();
				it.remove();
				if (element.timeout.isBefore(now)) {
					element = null; // Element is timed out.
				} else if (!availableGuilds.contains(element.serverId) || element.nextTry.isAfter(now)) {
					bufferedElements.put(element.serverId, element);
					element = null;
				}
			}
			todoQueue.putAll(bufferedElements);
			return element;
		}
	}

	/*package for test*/ void updateVote() {
		final @CheckForNull TodoElement element = getNextElement();
		if (element == null) {
			return;
		}

		try (Transaction trans = db.getTransaction(element.serverId)) {
			final Vote vote = db.getVote(trans, element.messageId);
			bot.updateMessage(element.serverId, vote.channelId, element.messageId, CommandUtil.createVoteText(vote));
		} catch (NotFoundException e) {
			// Nothing to do. We just throw away the element.
		} catch (Throwable t) { // NOSONAR
			element.tries.incrementAndGet();
			Log.getLog(this).log(Level.SEVERE, "Cant update vote " + element.messageId + " in try " + element.tries, t);
			if (element.tries.get() < maxTries) {
				element.nextTry = element.nextTry.plus(tryIntervall);
				synchronized (todoQueue) {
					todoQueue.put(element.serverId, element);
				}
			}
		}
	}

	@Override
	public void close() {
		executorService.shutdown();
		try {
			executorService.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException e) { // NOSONAR We are shutting down, just continue shutdown
		}
		saveTodo();
	}

	/*package for test*/ void searchForVotesToEditLast() {
		try {
			final Collection<Pair<Long, Long>> toUpdateVoteIds = db
					.getVotesToLastUpdate(Instant.now().getEpochSecond());
			for (final Pair<Long, Long> toUpdateVoteId : toUpdateVoteIds) {
				addToUpdateVote(toUpdateVoteId.getLeft(), toUpdateVoteId.getRight());
			}
			db.markVotesAsLastUpdated(toUpdateVoteIds);
		} catch (Throwable t) { // NOSONAR This task should not be cancelled
			Log.getLog(this).log(Level.SEVERE, "Cant search for votes to edit last", t);
		}
	}

	/*package for test*/ void deleteOldVotes() {
		try {
			final long deleteBefore = Instant.now().minus(deleteVotesOffsetDays).getEpochSecond();
			final int deleted = db.deleteOldVotes(deleteBefore);
			Log.getLog(this).info("Deleted " + deleted + " old votes");
		} catch (Throwable t) { // NOSONAR This task should not be cancelled
			Log.getLog(this).log(Level.SEVERE, "Cant delete old votes", t);
		}
	}

	public long todoQueueLength() {
		synchronized (todoQueue) {
			return todoQueue.size();
		}
	}
}

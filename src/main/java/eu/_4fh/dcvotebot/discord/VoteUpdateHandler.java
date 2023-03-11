package eu._4fh.dcvotebot.discord;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
import net.dv8tion.jda.api.events.guild.GuildUnavailableEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

@DefaultAnnotation(NonNull.class)
public class VoteUpdateHandler extends ListenerAdapter implements AutoCloseable {
	private static class TodoElement {
		public final long serverId;
		public final long messageId;
		public final Instant timeout;

		private TodoElement(final long serverId, final long messageId, final Instant timeout) {
			this.serverId = serverId;
			this.messageId = messageId;
			this.timeout = timeout;
		}
	}

	private final Bot bot;
	private final Db db;
	private final ScheduledExecutorService executorService;
	private final Queue<TodoElement> todoQueue;
	private final Set<Long> availableGuilds;
	private final Duration updateTimeout;

	public VoteUpdateHandler(Bot bot) {
		this.bot = bot;
		updateTimeout = Duration.ofMinutes(Config.instance().updateVotesTimeoutMinutes);
		this.db = Db.instance();
		this.todoQueue = new ConcurrentLinkedQueue<>();
		loadTodo();
		this.availableGuilds = ConcurrentHashMap.newKeySet();
		this.executorService = new ScheduledThreadPoolExecutor(1);
	}

	@Override
	public void onGuildReady(GuildReadyEvent event) {
		availableGuilds.add(event.getGuild().getIdLong());
	}

	@Override
	public void onGuildJoin(GuildJoinEvent event) {
		availableGuilds.add(event.getGuild().getIdLong());
	}

	@Override
	public void onGuildAvailable(GuildAvailableEvent event) {
		availableGuilds.add(event.getGuild().getIdLong());
		Log.getLog(this).warning(
				"Guild " + event.getGuild().getName() + "(" + event.getGuild().getId() + ") is now available again");
	}

	@Override
	public void onGuildUnavailable(GuildUnavailableEvent event) {
		availableGuilds.remove(event.getGuild().getIdLong());
		Log.getLog(this).warning(
				"Guild " + event.getGuild().getName() + "(" + event.getGuild().getId() + ") is now unavailable");
	}

	@Override
	public void onGuildLeave(GuildLeaveEvent event) {
		availableGuilds.remove(event.getGuild().getIdLong());
	}

	private void saveTodo() {
		final List<Long> messageIds = todoQueue.stream().map(e -> e.messageId).collect(Collectors.toUnmodifiableList());
		db.saveToUpdateVotes(messageIds);
	}

	private void loadTodo() {
		final Collection<Pair<Long, Long>> todos = db.loadToUpdateVotes();
		todos.forEach(
				serverAndMessageId -> addToUpdateVote(serverAndMessageId.getLeft(), serverAndMessageId.getRight()));
	}

	public void addToUpdateVote(final long serverId, final long messageId) {
		final TodoElement element = new TodoElement(serverId, messageId, Instant.now().plus(updateTimeout));
		todoQueue.add(element);
	}

	public void start() {
		final long intervall = Config.instance().updateIntervalMilliseconds;
		executorService.scheduleWithFixedDelay(this::updateVote, intervall, intervall, TimeUnit.MILLISECONDS);
	}

	private @CheckForNull TodoElement getNextElement() {
		final Instant now = Instant.now();
		final List<TodoElement> bufferedElements = new ArrayList<>();
		@CheckForNull
		TodoElement element = null;
		while (element == null && !todoQueue.isEmpty()) {
			element = todoQueue.poll();
			if (element != null) {
				// Race condition. Probably another thread removed the element between the while-condition and poll
				if (element.timeout.isBefore(now)) {
					element = null;
				} else if (!availableGuilds.contains(element.serverId)) {
					bufferedElements.add(element);
					element = null;
				}
			}
		}
		todoQueue.addAll(bufferedElements);
		return element;
	}

	private void updateVote() {
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
			Log.getLog(this).log(Level.SEVERE, "Cant update vote " + element.messageId, t);
			throw t;
		}
	}

	@Override
	public void close() {
		executorService.shutdownNow();
		try {
			executorService.awaitTermination(1, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		saveTodo();
	}
}

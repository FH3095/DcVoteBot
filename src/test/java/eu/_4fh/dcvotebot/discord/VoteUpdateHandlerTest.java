package eu._4fh.dcvotebot.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;
import org.easymock.EasyMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu._4fh.dcvotebot.db.Db;
import eu._4fh.dcvotebot.db.Vote;
import eu._4fh.dcvotebot.db.VoteOption;
import eu._4fh.dcvotebot.db.VoteSettings;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;

class VoteUpdateHandlerTest {
	private static final long SERVER_ID = 2_000_000L;
	private static final long CHANNEL_ID = 3_000_000L;

	private Db db;
	private long voteId = 0;

	private Bot bot;
	private VoteUpdateHandler handler;

	@BeforeEach
	void setupHandler() {
		db = Db.forTestNewDb();
		voteId++;
		final Vote vote = Vote.create(VoteSettings.getDefault(), CHANNEL_ID, "Vote" + voteId, "Test Vote",
				List.of(VoteOption.create("Opt1"), VoteOption.create("Opt2"), VoteOption.create("Opt3")));
		try (Db.Transaction trans = db.getTransaction(SERVER_ID)) {
			db.insertVote(trans, voteId, vote);
		}

		bot = EasyMock.strictMock(Bot.class);
		handler = null;
	}

	@AfterEach
	void testMock() {
		EasyMock.verify(bot);
		handler.close();
	}

	@Test
	void testUpdateVote() throws InterruptedException {
		bot.updateMessage(EasyMock.eq(SERVER_ID), EasyMock.eq(CHANNEL_ID), EasyMock.eq(voteId),
				EasyMock.contains("**Vote1**\n\nTest Vote\n\n"));
		expectLastCall();
		EasyMock.replay(bot);
		handler = new VoteUpdateHandler(bot);

		final Guild guild = EasyMock.strictMock(Guild.class);
		expect(guild.getIdLong()).andStubReturn(SERVER_ID);
		final GuildReadyEvent event = EasyMock.strictMock(GuildReadyEvent.class);
		expect(event.getGuild()).andStubReturn(guild);
		EasyMock.replay(guild, event);
		handler.onGuildReady(event);

		handler.addToUpdateVote(SERVER_ID, voteId);
		handler.addToUpdateVote(SERVER_ID, voteId);
		handler.updateVote();
	}

	@Test
	void testSaveTodo() {
		EasyMock.replay(bot);
		handler = new VoteUpdateHandler(bot);
		handler.addToUpdateVote(SERVER_ID, voteId);
		handler.close();
		final Collection<Pair<Long, Long>> toUpdateVotes = db.loadAndDeleteToUpdateVotes(SERVER_ID);
		assertThat(toUpdateVotes).containsExactlyInAnyOrder(Pair.of(SERVER_ID, voteId));
	}

	@Test
	void testDeleteOldVotes() {
		EasyMock.replay(bot);
		handler = new VoteUpdateHandler(bot);
		assertThatNoException().isThrownBy(handler::deleteOldVotes);
	}

	@Test
	void testAddToLastEditVote() throws InterruptedException {
		EasyMock.replay(bot);
		handler = new VoteUpdateHandler(bot);

		final VoteSettings settings = VoteSettings.create(Duration.ofSeconds(1), (byte) 2, false, ZoneId.of("UTC"));
		try (Db.Transaction trans = db.getTransaction(SERVER_ID)) {
			final Vote vote2 = Vote.createWithDefaults(settings, "Test2", "Test Desc 2", db.getVote(trans, voteId));
			voteId++;
			db.insertVote(trans, voteId, vote2);
		}
		TimeUnit.SECONDS.sleep(2); // NOSONAR Just 2 seconds
		Collection<Pair<Long, Long>> toUpdateVotes = db.getVotesToLastUpdate(Instant.now().getEpochSecond());
		assertThat(toUpdateVotes).containsExactlyInAnyOrder(Pair.of(SERVER_ID, voteId));
		handler.searchForVotesToEditLast();
		toUpdateVotes = db.getVotesToLastUpdate(Instant.now().getEpochSecond());
		assertThat(toUpdateVotes).isEmpty(); // Handler should have marked the edits as done
	}

	@Test
	void testStartAndStop() {
		EasyMock.replay(bot);
		handler = new VoteUpdateHandler(bot);
		assertThatNoException().isThrownBy(handler::start);
		assertThatNoException().isThrownBy(handler::close);
	}
}

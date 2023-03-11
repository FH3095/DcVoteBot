package eu._4fh.dcvotebot.discord;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu._4fh.dcvotebot.db.Db;

class DoVoteHandlerTest {
	private DoVoteHandler handler;
	private Db db;
	private static long voteCnt = 1;

	@BeforeEach
	void setupHandler() {
		db = Db.forTestNewDb();
		handler = new DoVoteHandler(DiscordMocks.botMock());
	}

	private long setupVote(final Instant voteStart) {
		final long voteId = voteCnt++;
		handler.startVote(null);
		return voteId;
	}

	@Test
	void testVote() {

	}
}

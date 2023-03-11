package eu._4fh.dcvotebot.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu._4fh.dcvotebot.db.Db.NotFoundException;
import eu._4fh.dcvotebot.db.Db.Transaction;

class DbTest {
	private Db db;

	@BeforeEach
	void before() {
		db = Db.forTestNewDb();
	}

	@Test
	void readWriteReadSettings() {
		VoteSettings settings;
		try (Transaction trans = db.getTransaction(1)) {
			settings = db.getDefaultSettings(trans);
		}
		assertThat(settings).isEqualTo(VoteSettings.getDefault());
		final VoteSettings newSettings = new VoteSettings(settings.answersPerUser, settings.duration.getSeconds(),
				!settings.canChangeAnswers, "Europe/Berlin");
		try (Transaction trans = db.getTransaction(1)) {
			db.setDefaultSettings(trans, newSettings);
		}
		try (Transaction trans = db.getTransaction(1)) {
			settings = db.getDefaultSettings(trans);
		}
		assertThat(settings).isEqualTo(newSettings);
		db.forTestResetCaches();
		try (Transaction trans = db.getTransaction(1)) {
			settings = db.getDefaultSettings(trans);
		}
		assertThat(settings).isEqualTo(newSettings);
	}

	@Test
	void readVoteDifferentServerFails() {
		final Vote vote = Vote.create(VoteSettings.getDefault(), 1, "Title", "desc",
				Collections.singletonList(VoteOption.create("Opt1")));
		try (Transaction trans = db.getTransaction(1)) {
			db.insertVote(trans, 1, vote);
		}
		try (Transaction trans = db.getTransaction(2)) {
			assertThatThrownBy(() -> db.getVote(trans, 1)).isInstanceOf(NotFoundException.class);
		}
	}

	@Test
	void readWriteReadVote() {
		final VoteOption opt1 = VoteOption.create("Opt1");
		final VoteOption opt2 = VoteOption.create("Opt2");
		final Vote newVote = Vote.create(VoteSettings.getDefault(), 100, "Title", "Desc", Arrays.asList(opt1, opt2));

		try (Transaction trans = db.getTransaction(1L)) {
			db.insertVote(trans, 1L, newVote);
		}

		Vote vote;
		try (Transaction trans = db.getTransaction(1L)) {
			vote = db.getVote(trans, 1L);
		}
		assertThat(vote.title).isEqualTo(newVote.title);
		assertThat(vote.description).isEqualTo(newVote.description);
		assertThat(vote.settings).isEqualTo(newVote.settings);
		assertThat(vote.options).map(o -> o.name).containsExactly("Opt1", "Opt2");

		db.forTestResetCaches();
		try (Transaction trans = db.getTransaction(1L)) {
			vote = db.getVote(trans, 1L);
		}
		assertThat(vote.title).isEqualTo(newVote.title);
		assertThat(vote.description).isEqualTo(newVote.description);
		assertThat(vote.settings).isEqualTo(newVote.settings);
		assertThat(vote.options).map(o -> o.name).containsExactly("Opt1", "Opt2");
	}

	@Test
	void testDoVote() {
		Vote vote = Vote.create(VoteSettings.getDefault(), 100, "Title", "Desc",
				Arrays.asList(VoteOption.create("Opt1"), VoteOption.create("Opt2"), VoteOption.create("Opt3")));

		try (Transaction trans = db.getTransaction(1L)) {
			db.insertVote(trans, 1L, vote);
			vote = db.getVote(trans, 1L);
		}

		try (Transaction trans = db.getTransaction(1L)) {
			db.updateVoteVotes(trans, 2L, 1L, Set.of(vote.options.get(0).id, vote.options.get(2).id));
		}
		try (Transaction trans = db.getTransaction(1L)) {
			vote = db.getVote(trans, 1L);
		}
		assertThat(vote.options).hasSize(3);
		assertThat(vote.options).map(option -> option.voters).containsExactly(Set.of(2L), Set.of(), Set.of(2L));
		db.forTestResetCaches();
		try (Transaction trans = db.getTransaction(1L)) {
			vote = db.getVote(trans, 1L);
		}
		assertThat(vote.options).hasSize(3);
		assertThat(vote.options).map(option -> option.voters).containsExactly(Set.of(2L), Set.of(), Set.of(2L));
	}

	@Test
	void testUpdateVote() {
		Vote vote = Vote.create(VoteSettings.getDefault(), 1L, "Title", "Desc", List.of(VoteOption.create("Opt1")));
		try (Transaction trans = db.getTransaction(1)) {
			db.insertVote(trans, 1, vote);
		}
		try (Transaction trans = db.getTransaction(1)) {
			vote = db.getVote(trans, 1);
		}

		vote = Vote.createWithDefaults(
				VoteSettings.createWithDefaults(Duration.ofDays(5), (byte) 2, true, vote.settings), "Title2", "Desc2",
				vote);
		Vote voteFromDb;
		try (Transaction trans = db.getTransaction(1)) {
			db.updateVote(trans, 1, vote);
			voteFromDb = db.getVote(trans, 1);
		}
		assertThat(voteFromDb.title).isEqualTo("Title2");
		assertThat(voteFromDb).isEqualTo(vote);

		db.forTestResetCaches();
		try (Transaction trans = db.getTransaction(1)) {
			voteFromDb = db.getVote(trans, 1);
		}
		assertThat(voteFromDb.title).isEqualTo("Title2");
		assertThat(voteFromDb).isEqualTo(vote);
	}

	@Test
	void testGetAllServerVotes() {
		try (Transaction trans = db.getTransaction(1)) {
			final Vote vote = Vote.create(VoteSettings.getDefault(), 1L, "Title", "Desc",
					List.of(VoteOption.create("Opt")));
			db.insertVote(trans, Long.MAX_VALUE, vote);
			db.insertVote(trans, Long.MIN_VALUE, vote);
			db.insertVote(trans, -1, vote);
		}

		try (Transaction trans = db.getTransaction(2)) {
			assertThat(db.getAllServerVoteIds(trans)).isEmpty();
		}
		try (Transaction trans = db.getTransaction(1)) {
			assertThat(db.getAllServerVoteIds(trans)).containsExactlyInAnyOrder(-1L, Long.MAX_VALUE, Long.MIN_VALUE);
		}
	}
}

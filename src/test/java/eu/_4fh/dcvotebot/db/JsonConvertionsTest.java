package eu._4fh.dcvotebot.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class JsonConvertionsTest {
	@Test
	void testVoteSettings() {
		final VoteSettings in = new VoteSettings(2, 3, false);
		final VoteSettings out = VoteSettings.fromJson(in.toJson());
		assertThat(out.answersPerUser).isEqualTo(2);
		assertThat(out.duration.getSeconds()).isEqualTo(3);
		assertThat(out.canChangeAnswers).isFalse();
	}

	@Test
	void testVote() {
		VoteOption opt1 = new VoteOption("Opt1");
		VoteOption opt2 = new VoteOption("Opt2");
		Vote vote = new Vote(VoteSettings.getDefault(), "Title", "Desc", Arrays.asList(opt1, opt2));
		final Instant origStart = vote.start;
		opt1.voters.add(1L);
		opt2.voters.add(2L);

		vote = Vote.fromJson(vote.toJson());
		opt1 = vote.options.get(0);
		opt2 = vote.options.get(1);

		assertThat(vote.title).isEqualTo("Title");
		assertThat(vote.description).isEqualTo("Desc");
		assertThat(vote.start).isEqualTo(origStart);
		assertThat(vote.options).hasSize(2);
		assertThat(opt1.name).isEqualTo("Opt1");
		assertThat(opt2.name).isEqualTo("Opt2");
		assertThat(opt1.voters).containsExactlyInAnyOrder(1L);
		assertThat(opt2.voters).containsExactlyInAnyOrder(2L);
	}
}

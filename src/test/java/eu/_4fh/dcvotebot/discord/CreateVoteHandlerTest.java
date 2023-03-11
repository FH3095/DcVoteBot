package eu._4fh.dcvotebot.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.assertj.core.data.TemporalUnitWithinOffset;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu._4fh.dcvotebot.db.Db;
import eu._4fh.dcvotebot.db.Db.Transaction;
import eu._4fh.dcvotebot.db.Vote;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.interactions.ModalCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

class CreateVoteHandlerTest {
	private CreateVoteHandler handler;

	@BeforeEach
	void setupHandler() {
		handler = new CreateVoteHandler(DiscordMocks.botMock());
	}

	@Test
	void testSlashCommand() {
		final SlashCommandInteractionEvent slashEvent = DiscordMocks.slashCommandInteractionEvent("create-vote");
		final Capture<Modal> modalCapture = EasyMock.newCapture(CaptureType.FIRST);
		final ModalCallbackAction callback = EasyMock.niceMock(ModalCallbackAction.class);
		expect(slashEvent.replyModal(EasyMock.capture(modalCapture))).andStubReturn(callback);
		EasyMock.replay(slashEvent, callback);

		handler.onSlashCommandInteraction(slashEvent);

		final Modal modal = modalCapture.getValue();
		final String modalId = modal.getId();
		final String expectedModalId = handler.idPrefix + CreateVoteHandler.START_VOTE_DIALOG_PREFIX;
		assertThat(modalId).isEqualTo(expectedModalId);

		final ModalInteractionEvent modalEvent = DiscordMocks.modalInteractionEvent(modalId, 2L, 3L);
		EasyMock.replay(modalEvent);
		handler.onModalInteraction(modalEvent);

		final Vote vote;
		final Db db = Db.instance();
		try (Transaction trans = db.getTransaction(2L)) {
			vote = db.getVote(trans, 3);
		}
		assertThat(vote).isNotNull();
		assertThat(vote.title).isEqualTo("Test Title");
		assertThat(vote.description).isEqualTo("Test Description");
		assertThat(vote.start).isCloseTo(Instant.now(), new TemporalUnitWithinOffset(1, ChronoUnit.MINUTES));
		assertThat(vote.settings.answersPerUser).isEqualTo((byte) 2);
		assertThat(vote.settings.duration).isEqualTo(Duration.ofMinutes(1 * 24 * 60 + 2 * 60 + 3));
		assertThat(vote.settings.canChangeAnswers).isTrue();
		assertThat(vote.options).map(option -> option.voters).allMatch(Set::isEmpty);
		assertThat(vote.options).map(option -> option.name).containsExactly("Opt 1", "Opt 2", "Opt 3", "Opt 4",
				"Opt 5");
	}

	@Test
	void testTimeout() {
		final ReplyCallbackAction callback = EasyMock.strictMock(ReplyCallbackAction.class);
		expect(callback.setEphemeral(true)).andReturn(callback).once();
		callback.queue();
		expectLastCall().once();

		final ModalInteractionEvent modalEvent = DiscordMocks
				.modalInteractionEvent(handler.idPrefix + CreateVoteHandler.START_VOTE_DIALOG_PREFIX, 2L, 3L);
		expect(modalEvent.reply("Your vote creation timed out. Please try again.")).andReturn(callback).once();
		EasyMock.replay(modalEvent, callback);

		handler.onModalInteraction(modalEvent);

		EasyMock.verify(callback, modalEvent);
	}
}

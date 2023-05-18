package eu._4fh.dcvotebot.discord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.easymock.EasyMock.expect;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.easymock.EasyMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu._4fh.dcvotebot.db.Db;
import eu._4fh.dcvotebot.db.Vote;
import eu._4fh.dcvotebot.db.VoteOption;
import eu._4fh.dcvotebot.db.VoteSettings;
import eu._4fh.dcvotebot.discord.DoVoteHandler.VoteData;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;

class DoVoteHandlerTest {
	private DoVoteHandler handler;
	private Db db;
	private static long userId = 1_000_000;
	private static long serverId = 2_000_000;
	private static long channelId = 3_000_000;
	private long nextVoteMsgId = 4_000_000;

	@BeforeEach
	void setupHandler() {
		db = Db.forTestNewDb();
		handler = new DoVoteHandler(DiscordMocks.botMock());
	}

	private static WebhookMessageCreateAction<Message> stubMessageCreateRequest() {
		final WebhookMessageCreateAction<Message> request = EasyMock.strictMock(WebhookMessageCreateAction.class);
		DiscordMocks.stubCallback(request);
		expect(request.addActionRow((ItemComponent) EasyMock.anyObject())).andStubReturn(request);
		EasyMock.replay(request);
		return request;
	}

	private static InteractionHook interactionHook(final WebhookMessageCreateAction<Message> msgCreateAction) {
		final InteractionHook result = EasyMock.strictMock(InteractionHook.class);
		expect(result.sendMessage(EasyMock.anyString())).andStubReturn(msgCreateAction);
		EasyMock.replay(result);
		return result;
	}

	private static InteractionHook editHook(final String msg) {
		final WebhookMessageEditAction<Message> editAction = EasyMock.strictMock(WebhookMessageEditAction.class);
		DiscordMocks.stubCallback(editAction);
		expect(editAction.setComponents(Collections.emptyList())).andStubReturn(editAction);

		final InteractionHook interactionHook = EasyMock.strictMock(InteractionHook.class);
		expect(interactionHook.editOriginal(msg)).andReturn(editAction);
		EasyMock.replay(editAction, interactionHook);
		return interactionHook;
	}

	private static SelectOption selectOption(final String value) {
		return SelectOption.of("Name " + value, value);
	}

	private long setupVote(final Instant start) {
		final VoteSettings settings = VoteSettings.create(Duration.ofDays(1), (byte) 2, false, ZoneId.of("UTC"));
		final Vote vote = Vote.createForTest(settings, channelId, "Test Vote", "Test Desc",
				Arrays.asList(VoteOption.create("Opt1"), VoteOption.create("Opt2"), VoteOption.create("Opt3")), start);
		try (Db.Transaction trans = db.getTransaction(serverId)) {
			db.insertVote(trans, nextVoteMsgId, vote);
		}
		return nextVoteMsgId++;
	}

	@Test
	void testVote() {
		final long msgId = setupVote(Instant.now());
		final ButtonInteractionEvent startBtnEvent = EasyMock.strictMock(ButtonInteractionEvent.class);
		expect(startBtnEvent.deferReply(true)).andStubReturn(DiscordMocks.stubReply());
		expect(startBtnEvent.getMessageIdLong()).andStubReturn(msgId);
		expect(startBtnEvent.getGuild()).andStubReturn(DiscordMocks.guild(serverId));
		expect(startBtnEvent.getUser()).andStubReturn(DiscordMocks.user(userId));
		expect(startBtnEvent.getHook()).andStubReturn(interactionHook(stubMessageCreateRequest()));
		EasyMock.replay(startBtnEvent);
		handler.startVote(startBtnEvent);

		final StringSelectInteractionEvent ssiEvent = EasyMock.strictMock(StringSelectInteractionEvent.class);
		expect(ssiEvent.getComponentId()).andStubReturn(handler.idPrefix + "WhatEver");
		expect(ssiEvent.getUser()).andStubReturn(DiscordMocks.user(userId));
		expect(ssiEvent.getSelectedOptions()).andStubReturn(List.of(selectOption("0"), selectOption("2")));
		expect(ssiEvent.deferEdit()).andStubReturn(DiscordMocks.stubEdit());
		EasyMock.replay(ssiEvent);
		handler.onStringSelectInteraction(ssiEvent);

		final ButtonInteractionEvent voteBtnEvent = EasyMock.strictMock(ButtonInteractionEvent.class);
		expect(voteBtnEvent.getComponentId()).andStubReturn(handler.idPrefix + DoVoteHandler.SEND_VOTE_BUTTON_PREFIX);
		expect(voteBtnEvent.getUser()).andStubReturn(DiscordMocks.user(userId));
		expect(voteBtnEvent.getGuild()).andStubReturn(DiscordMocks.guild(serverId));
		expect(voteBtnEvent.deferEdit()).andStubReturn(DiscordMocks.stubEdit());

		final InteractionHook interactionHook = editHook(
				"Saved your answer. It could take some minutes until the poll-message shows your answers.");
		expect(voteBtnEvent.getHook()).andStubReturn(interactionHook);
		EasyMock.replay(voteBtnEvent);
		handler.onButtonInteraction(voteBtnEvent);
		EasyMock.verify(startBtnEvent, ssiEvent, voteBtnEvent, interactionHook);

		try (Db.Transaction trans = db.getTransaction(serverId)) {
			final Vote vote = db.getVote(trans, msgId);
			assertThat(vote.options).map(option -> option.voters).containsExactly(Set.of(userId), Set.of(),
					Set.of(userId));
		}
	}

	private VoteData createVoteData(final long voteMsgId, final int... votes) {
		final VoteData data = new VoteData(voteMsgId);
		for (int vote : votes) {
			data.votes.set(vote);
		}
		return data;
	}

	private ButtonInteractionEvent simpleButtonInteraction(final InteractionHook interactionHook) {
		final ButtonInteractionEvent event = EasyMock.strictMock(ButtonInteractionEvent.class);
		expect(event.getUser()).andStubReturn(DiscordMocks.user(userId));
		expect(event.getHook()).andStubReturn(interactionHook);
		EasyMock.replay(event);
		return event;
	}

	private void testVoteFailedMsg(final VoteData voteData, final long voteMsgId, final String expectedMsg) {
		final InteractionHook hook = editHook(expectedMsg);
		final ButtonInteractionEvent event = simpleButtonInteraction(hook);
		try (Db.Transaction trans = db.getTransaction(serverId)) {
			final Vote vote = db.getVote(trans, voteMsgId);
			handler.handleVote(db, event, voteData, vote);
		}
		EasyMock.verify(event, hook);
	}

	@Test
	void testVoteEnded() {
		final long msgId = setupVote(Instant.now().minus(2L, ChronoUnit.DAYS));
		testVoteFailedMsg(createVoteData(msgId, 0, 2), msgId, "This poll already ended.");
	}

	@Test
	void testAlreadyVoted() {
		final long msgId = setupVote(Instant.now());
		try (Db.Transaction trans = db.getTransaction(serverId)) {
			final Vote vote = db.getVote(trans, msgId);
			db.updateVoteVotes(trans, userId, msgId, Set.of(vote.options.get(0).id));
		}
		testVoteFailedMsg(createVoteData(msgId, 0, 2), msgId, "You already voted and can't change your answer.");
	}

	@Test
	void testNoAnswerSelected() {
		final long msgId = setupVote(Instant.now());
		testVoteFailedMsg(createVoteData(msgId), msgId, "You didn't select any answer.");
	}

	@Test
	void testTooManyAnswersSelected() {
		final long msgId = setupVote(Instant.now());
		testVoteFailedMsg(createVoteData(msgId, 0, 1, 2), msgId, "You selected more than 2 answers.");
	}
}

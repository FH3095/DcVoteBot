package eu._4fh.dcvotebot.discord;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;

import org.easymock.EasyMock;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.modals.ModalInteraction;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

/*package*/ class DiscordMocks {
	public static final long userId = 30;
	public static final long channelId = 40;

	private DiscordMocks() {
	}

	/*package*/ static Bot botMock() {
		final Bot bot = EasyMock.strictMock(Bot.class);
		bot.updateVoteText(EasyMock.anyLong(), EasyMock.anyLong());
		expectLastCall().asStub();
		EasyMock.replay(bot);
		return bot;
	}

	/*package*/ static User user(final long id) {
		final User user = EasyMock.strictMock(User.class);
		expect(user.getIdLong()).andStubReturn(id);
		EasyMock.replay(user);
		return user;
	}

	/*package*/ static MessageChannelUnion channel(final long id) {
		final MessageChannelUnion channel = EasyMock.strictMock(MessageChannelUnion.class);
		expect(channel.getIdLong()).andStubReturn(id);
		EasyMock.replay(channel);
		return channel;
	}

	/*package*/ static SlashCommandInteractionEvent slashCommandInteractionEvent(final String command) {
		final SlashCommandInteractionEvent event = EasyMock.strictMock(SlashCommandInteractionEvent.class);
		expect(event.getUser()).andStubReturn(user(userId));
		expect(event.getFullCommandName()).andStubReturn(command);
		addOption(event, "title", "Test Title");
		addOption(event, "description", "Test Description");
		addOption(event, "duration", "1d 2h 3m");
		addOption(event, "answers-per-user", "2");
		addOption(event, "users-can-change-answer", "true");
		return event;
	}

	/*package*/ static void addOption(final SlashCommandInteractionEvent event, final String name, final String value) {
		final OptionMapping option = EasyMock.strictMock(OptionMapping.class);
		expect(option.getName()).andStubReturn(name);
		expect(option.getAsString()).andStubReturn(value);
		expect(option.getAsLong()).andStubAnswer(() -> Long.parseLong(value));
		expect(option.getAsBoolean()).andStubReturn(Boolean.parseBoolean(value));
		EasyMock.replay(option);
		expect(event.getOption(name)).andStubReturn(option);
	}

	/*package*/ static ModalInteractionEvent modalInteractionEvent(final String modalId, final long serverId,
			final long messageId) {
		final ReplyCallbackAction callback = EasyMock.niceMock(ReplyCallbackAction.class);
		EasyMock.replay(callback);

		final ModalInteractionEvent event = EasyMock.strictMock(ModalInteractionEvent.class);
		expect(event.getModalId()).andStubReturn(modalId);
		expect(event.deferReply(EasyMock.anyBoolean())).andStubReturn(callback);
		expect(event.getUser()).andStubReturn(user(userId));
		expect(event.getChannel()).andStubReturn(channel(channelId));

		// Modal data
		final ModalMapping modalMapping = EasyMock.strictMock(ModalMapping.class);
		expect(modalMapping.getAsString()).andStubReturn("Opt 1\r\rOpt 2\r\n\r\nOpt 3\n\nOpt 4\nOpt 5");
		EasyMock.replay(modalMapping);
		final ModalInteraction modalInteraction = EasyMock.strictMock(ModalInteraction.class);
		expect(modalInteraction.getValue("PollOptions")).andStubReturn(modalMapping);
		EasyMock.replay(modalInteraction);
		expect(event.getInteraction()).andStubReturn(modalInteraction);

		// Hook
		final InteractionHook hook = EasyMock.strictMock(InteractionHook.class);
		expect(event.getHook()).andStubReturn(hook);

		// Get original message id
		final Message message = EasyMock.strictMock(Message.class);
		expect(message.getIdLong()).andStubReturn(messageId);
		final RestAction<Message> restAction = EasyMock.strictMock(RestAction.class);
		expect(restAction.complete()).andStubReturn(message);
		expect(hook.retrieveOriginal()).andStubReturn(restAction);
		EasyMock.replay(message, restAction);

		// Guild id
		final Guild guild = EasyMock.strictMock(Guild.class);
		expect(guild.getIdLong()).andStubReturn(serverId);
		expect(event.getGuild()).andStubReturn(guild);
		EasyMock.replay(guild);

		// Send message
		final WebhookMessageCreateAction<Message> msgCreateAction = EasyMock
				.strictMock(WebhookMessageCreateAction.class);
		expect(msgCreateAction.addActionRow((ItemComponent) EasyMock.anyObject())).andStubReturn(msgCreateAction);
		msgCreateAction.queue();
		expectLastCall().asStub();
		expect(hook.sendMessage(EasyMock.anyString())).andStubReturn(msgCreateAction);
		EasyMock.replay(msgCreateAction);

		EasyMock.replay(hook);

		return event;
	}

	/*package*/ static void stubCallback(final RestAction<?> mock) {
		mock.queue();
		expectLastCall().asStub();
		expect(mock.complete()).andStubReturn(null);
	}

	/*package*/ static ReplyCallbackAction stubReply() {
		final ReplyCallbackAction result = EasyMock.strictMock(ReplyCallbackAction.class);
		stubCallback(result);
		EasyMock.replay(result);
		return result;
	}

	/*package*/ static MessageEditCallbackAction stubEdit() {
		final MessageEditCallbackAction result = EasyMock.strictMock(MessageEditCallbackAction.class);
		stubCallback(result);
		EasyMock.replay(result);
		return result;
	}

	/*package*/ static Guild guild(final long id) {
		final Guild guild = EasyMock.strictMock(Guild.class);
		expect(guild.getIdLong()).andStubReturn(id);
		EasyMock.replay(guild);
		return guild;
	}
}

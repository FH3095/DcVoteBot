package eu._4fh.dcvotebot.discord;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.dcvotebot.util.Config;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

@DefaultAnnotation(NonNull.class)
public class Bot extends ListenerAdapter implements AutoCloseable {
	private final JDA jda;
	private final DoVoteHandler doVoteHandler;

	public Bot(final int shardId, final int shardTotal) {
		doVoteHandler = new DoVoteHandler(this);
		final AbstractCommandHandler<?>[] commands = { new CreateVoteHandler(this), doVoteHandler,
				new EditVoteHandler(this), new DeleteVoteHandler(this), new VoteSettingsDefaultCommand(this) };

		final JDABuilder jdaBuilder = JDABuilder
				.createDefault(Config.instance().discordToken, GatewayIntent.GUILD_MESSAGE_REACTIONS)
				.disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
				.setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.NONE)
				.setChunkingFilter(ChunkingFilter.NONE).addEventListeners((Object[]) commands);
		if (shardTotal > 1) {
			jdaBuilder.useSharding(shardId, shardTotal);
		}
		jda = jdaBuilder.build();
		try {
			jda.awaitReady();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}

		if (shardId == 0) {
			for (final AbstractCommandHandler<?> commandHandler : commands) {
				final CommandData commandData = commandHandler.createCommandData();
				if (commandData != null) {
					jda.upsertCommand(commandData).queue();
				}
			}
		}
	}

	@Override
	public void close() {
		if (jda != null) {
			jda.shutdown();
			try {
				jda.awaitShutdown();
			} catch (InterruptedException e) {
				// Ignore, shutting down anyway
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		event.replyChoices(new Command.Choice("n1", "v1"), new Command.Choice("n2", "v2")).queue();
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if ("create-vote".equals(event.getName())) {
			event.deferReply(false).queue();
			event.getHook().sendMessage("Vote Menu").addActionRow(Button.primary("voteBtnId", "Vote")).queue();
		} else if ("edit-vote".equals(event.getName())) {
			event.reply(event.getOptions().toString()).setEphemeral(true).queue();
		} else {
			TextInput input = TextInput.create("tiid", "TextIn", TextInputStyle.PARAGRAPH).setRequired(true).build();
			Modal m = Modal.create("1", "Test").addActionRow(input).build();
			event.replyModal(m).queue();
		}
	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		event.deferReply(true).queue();
		final StringSelectMenu menu = StringSelectMenu.create("2").addOption("l1", "v1")
				.addOptions(SelectOption.of("l2", "v2").withDefault(true)).addOption("l3", "v3").setMinValues(1)
				.setMaxValues(2).build();
		event.getHook()
				.sendMessage(new MessageCreateBuilder().setContent("Vote").addComponents(ActionRow.of(menu)).build())
				.queue();
	}

	@Override
	public void onStringSelectInteraction(StringSelectInteractionEvent event) {
		event.deferEdit().queue();
		event.getHook().editOriginal("Voted").setReplace(true).queue();
	}

	@Override
	public void onModalInteraction(ModalInteractionEvent event) {
		event.reply("Done " + event.getModalId()).setEphemeral(true).queue();
	}

	/*package*/ void handleStartVote(final long voteId, ButtonInteractionEvent event) {
		doVoteHandler.startVote(voteId, event);
	}
}

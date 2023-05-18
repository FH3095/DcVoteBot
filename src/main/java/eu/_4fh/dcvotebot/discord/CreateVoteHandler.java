package eu._4fh.dcvotebot.discord;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import eu._4fh.dcvotebot.db.Db;
import eu._4fh.dcvotebot.db.Db.Transaction;
import eu._4fh.dcvotebot.db.Vote;
import eu._4fh.dcvotebot.db.VoteOption;
import eu._4fh.dcvotebot.db.VoteSettings;
import eu._4fh.dcvotebot.discord.CommandUtil.OptionValueException;
import eu._4fh.dcvotebot.discord.CreateVoteHandler.CreateVoteData;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

@DefaultAnnotation(NonNull.class)
/*package*/ class CreateVoteHandler extends AbstractCommandHandler<CreateVoteData> {
	/*package*/ static class CreateVoteData {
		private final String title;
		private final String description;
		private final @CheckForNull Duration duration;
		private final @CheckForNull Byte votesPerUser;
		private final @CheckForNull Boolean canChangeVote;

		private CreateVoteData(final String title, final String description, final @CheckForNull Duration duration,
				final @CheckForNull Byte votesPerUser, final @CheckForNull Boolean canChangeVote) {
			this.title = title;
			this.description = description;
			this.duration = duration;
			this.votesPerUser = votesPerUser;
			this.canChangeVote = canChangeVote;
		}
	}

	/*package for test*/ static final String START_VOTE_BUTTON_PREFIX = "pollBtn";
	/*package for test*/ static final String START_VOTE_DIALOG_PREFIX = "pollOptsDlg";

	/*package*/ CreateVoteHandler(final Bot bot) {
		super(bot, "cr-po", "create-poll");
	}

	@Override
	@SuppressFBWarnings(value = "NP_NULL_PARAM_DEREF", justification = "False-positive")
	protected SlashCommandData createCommandData() {
		SlashCommandData commandData = Commands.slash(command, "Creates a new poll").setGuildOnly(true);
		CommandUtil.addCreateVoteOptions(commandData, true);
		commandData.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_SEND));
		return commandData;
	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		if (!handlesEvent(event)) {
			return;
		}

		final String id = event.getComponentId();
		if (isComponent(START_VOTE_BUTTON_PREFIX, id)) {
			bot.handleStartVote(event);
		} else {
			throw new IllegalStateException("Cant handle button " + id);
		}
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!handlesCommand(event)) {
			return;
		}

		final CreateVoteData createVoteData;
		try {
			final String title = event.getOption("title").getAsString();
			final String description = event.getOption("description").getAsString();
			final Duration duration = CommandUtil.parseDuration(event.getOption("duration"));
			final Byte votesPerUser = CommandUtil.parseByte(event.getOption("answers-per-user"), (byte) 1, (byte) 25);
			final Boolean usersCanChangeVotes = CommandUtil.parseBoolean(event.getOption("users-can-change-answer"));
			createVoteData = new CreateVoteData(title, description, duration, votesPerUser, usersCanChangeVotes);
		} catch (OptionValueException e) {
			event.reply(e.getMessage()).setEphemeral(true).queue();
			return;
		}

		addCacheObject(event.getUser().getIdLong(), createVoteData);
		final TextInput input = TextInput.create("PollOptions", "Options", TextInputStyle.PARAGRAPH)
				.setPlaceholder("One poll-answer per line").setRequiredRange(1, 1000).setRequired(true).build();
		final Modal modal = Modal.create(generateComponentId(START_VOTE_DIALOG_PREFIX), "Poll-Answers")
				.addActionRow(input).build();
		event.replyModal(modal).queue();
	}

	private static final Pattern NEWLINE_SPLIT_PATTERN = Pattern.compile("[\\r\\n]+");

	@Override
	public void onModalInteraction(final ModalInteractionEvent event) {
		if (!handlesEvent(event)) {
			return;
		}

		final CreateVoteData createVoteData = getCacheObject(event.getUser().getIdLong());
		if (createVoteData == null) {
			event.reply("Your poll creation timed out. Please try again.").setEphemeral(true).queue();
			return;
		}

		final ModalMapping modal = event.getInteraction().getValue("PollOptions");
		final String optionsStr = modal.getAsString();
		final List<VoteOption> options = NEWLINE_SPLIT_PATTERN.splitAsStream(optionsStr)
				.filter(option -> option != null && !option.isBlank()).map(VoteOption::create)
				.collect(Collectors.toUnmodifiableList());

		final Db db = Db.instance();
		final VoteSettings settings;
		final Vote vote;
		try (Transaction trans = db.getTransaction(event.getGuild().getIdLong())) {
			final VoteSettings defaultSettings = db.getDefaultSettings(trans);
			settings = VoteSettings.createWithDefaults(createVoteData.duration, createVoteData.votesPerUser,
					createVoteData.canChangeVote, defaultSettings);
			vote = Vote.create(settings, event.getChannel().getIdLong(), createVoteData.title,
					createVoteData.description, options);
		}

		final String messageText = CommandUtil.createVoteText(vote);
		if (messageText.length() >= 2000) {
			event.reply(
					"The message for the poll would exceed 2000 characters. Either use a shorter description or shorter options. You currently would use "
							+ messageText.length() + " characters.")
					.setEphemeral(true).queue();
			return;
		}

		event.deferReply(false).complete();
		final long messageId = event.getHook().retrieveOriginal().complete().getIdLong();

		try (Transaction trans = db.getTransaction(event.getGuild().getIdLong())) {
			db.insertVote(trans, messageId, vote);
			event.getHook().sendMessage(messageText)
					.addActionRow(Button.primary(generateComponentId(START_VOTE_BUTTON_PREFIX), "Select Answer"))
					.queue();
		}
	}
}

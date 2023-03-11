package eu._4fh.dcvotebot.discord;

import java.time.Duration;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import eu._4fh.dcvotebot.db.Db;
import eu._4fh.dcvotebot.db.Db.NotFoundException;
import eu._4fh.dcvotebot.db.Db.Transaction;
import eu._4fh.dcvotebot.db.Vote;
import eu._4fh.dcvotebot.db.VoteSettings;
import eu._4fh.dcvotebot.discord.CommandUtil.OptionValueException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/*package*/ class EditVoteHandler extends AbstractCommandHandler<Void> {

	/*package*/ EditVoteHandler(final Bot bot) {
		super(bot, "ed-vo", "edit-vote");
	}

	@Override
	@SuppressFBWarnings(value = "NP_NULL_PARAM_DEREF", justification = "False-positive")
	protected SlashCommandData createCommandData() {
		final SlashCommandData commandData = Commands.slash(command, "Edits an existing vote").setGuildOnly(true);
		commandData.addOptions(new OptionData(OptionType.STRING, "vote", "The vote to edit", true, true));
		CommandUtil.addCreateVoteOptions(commandData, false);
		commandData.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MESSAGE_MANAGE));
		return commandData;
	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!handlesCommand(event)) {
			return;
		}
		CommandUtil.autoCompleteVotes(event);
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!handlesCommand(event)) {
			return;
		}

		event.deferReply(true).queue();

		try {
			final String title = event.getOption("title", OptionMapping::getAsString);
			final String description = event.getOption("description", OptionMapping::getAsString);
			final Duration duration = CommandUtil.parseDuration(event.getOption("duration"));
			final Byte votesPerUser = CommandUtil.parseByte(event.getOption("votes-per-user"), (byte) 1, (byte) 25);
			final Boolean usersCanChangeVotes = CommandUtil.parseBoolean(event.getOption("users-can-change-vote"));

			final Db db = Db.instance();
			try (Transaction trans = db.getTransaction(event.getGuild().getIdLong())) {
				final long voteId = event.getOption("vote", 0L, OptionMapping::getAsLong);
				final Vote vote = db.getVote(trans, voteId);
				final VoteSettings newSettings = VoteSettings.createWithDefaults(duration, votesPerUser,
						usersCanChangeVotes, vote.settings);
				final Vote newVote = Vote.createWithDefaults(newSettings, title, description, vote);
				db.updateVote(trans, voteId, newVote);
				event.getHook().sendMessage("Updated the vote. It could take some minutes to show up.").queue();
				bot.updateVoteText(event.getGuild().getIdLong(), voteId);
			}
		} catch (OptionValueException e) {
			event.getHook().sendMessage(e.getMessage()).queue();
		} catch (NotFoundException e) {
			event.getHook().sendMessage("Cant find the vote you tried to edit").queue();
		}
	}
}

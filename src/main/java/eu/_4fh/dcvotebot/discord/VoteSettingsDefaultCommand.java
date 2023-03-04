package eu._4fh.dcvotebot.discord;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Optional;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import eu._4fh.dcvotebot.db.Db;
import eu._4fh.dcvotebot.db.Db.LockHolder;
import eu._4fh.dcvotebot.db.VoteSettings;
import eu._4fh.dcvotebot.discord.CommandUtil.OptionValueException;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

@DefaultAnnotation(NonNull.class)
public class VoteSettingsDefaultCommand extends AbstractCommandHandler<Void> {

	protected VoteSettingsDefaultCommand(Bot bot) {
		super(bot, "set-vo-def", "set-vote-defaults");
	}

	@Override
	@SuppressFBWarnings(value = "NP_NULL_PARAM_DEREF", justification = "False-positive")
	protected SlashCommandData createCommandData() {
		SlashCommandData commandData = Commands.slash(command, "Sets default vote settings");
		CommandUtil.addVoteSettingsOptions(commandData, true);
		commandData.addOption(OptionType.STRING, "timezone", "Timezone for the votes start and end.", true);
		return commandData;
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!handlesCommand(event)) {
			return;
		}

		event.deferReply(true).queue();
		try {
			final Duration duration = CommandUtil.parseDuration(event.getOption("duration"));
			final Long votesPerUser = CommandUtil.parseLong(event.getOption("votes-per-user"), 1, 25);
			final Boolean usersCanChangeVote = CommandUtil.parseBoolean(event.getOption("users-can-change-vote"));
			final String timezoneStr = Optional.ofNullable(event.getOption("timezone")).map(OptionMapping::getAsString)
					.orElse(null);
			if (duration == null || votesPerUser == null || usersCanChangeVote == null || timezoneStr == null) {
				event.getHook().sendMessage("Missing one of the parameters.").queue();
				return;
			}
			final ZoneId timezone = ZoneId.of(timezoneStr);
			final VoteSettings settings = VoteSettings.create(duration, votesPerUser.intValue(),
					usersCanChangeVote.booleanValue(), timezone);

			final Db db = Db.instance();
			try (LockHolder lock = db.getLock(event.getGuild().getIdLong())) {
				db.setDefaultSettings(lock, settings);
			}
			event.getHook().sendMessage("Done").queue();
		} catch (OptionValueException e) {
			event.getHook().sendMessage(e.getMessage()).queue();
		} catch (DateTimeException e) {
			event.getHook().sendMessage("Invalid timezone.").queue();
		}
	}
}

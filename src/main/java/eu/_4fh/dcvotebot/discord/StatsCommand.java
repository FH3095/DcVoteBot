package eu._4fh.dcvotebot.discord;

import java.time.Duration;

import org.apache.commons.lang3.time.DurationFormatUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import eu._4fh.dcvotebot.util.Config;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public class StatsCommand extends AbstractCommandHandler<Void> {

	private final VoteUpdateHandler voteUpdateHandler;

	protected StatsCommand(Bot bot, VoteUpdateHandler voteUpdateHandler) {
		super(bot, "stats", "poll-stats");
		this.voteUpdateHandler = voteUpdateHandler;
	}

	@Override
	@SuppressFBWarnings(value = "NP_NULL_PARAM_DEREF", justification = "False-positive")
	protected SlashCommandData createCommandData() {
		SlashCommandData commandData = Commands.slash(command, "Gets poll bot statistics");
		commandData.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
		return commandData;
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!handlesCommand(event)) {
			return;
		}

		final long todoSize = voteUpdateHandler.todoQueueLength();
		final long expectedToUpdateTimeMillis = Duration.ofMillis(Config.instance().updateIntervalMilliseconds)
				.multipliedBy(todoSize).toMillis();
		event.reply("Expected time to update a poll: "
				+ DurationFormatUtils.formatDuration(expectedToUpdateTimeMillis, "HH:mm:ss")).setEphemeral(true)
				.queue();
	}
}

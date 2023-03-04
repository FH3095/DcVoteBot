package eu._4fh.dcvotebot.discord;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/*package*/ class DeleteVoteHandler extends AbstractCommandHandler<Void> {

	/*package*/ DeleteVoteHandler(final Bot bot) {
		super(bot, "de-vo", "delete-vote");
	}

	@Override
	protected SlashCommandData createCommandData() {
		final SlashCommandData commandData = Commands.slash(command, "Deletes an existing vote").setGuildOnly(true);
		commandData.addOptions(new OptionData(OptionType.STRING, "vote", "The vote to edit", true, true));
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
	}
}

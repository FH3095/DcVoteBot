package eu._4fh.dcvotebot.discord;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

/*package*/ class EditVoteHandler extends AbstractCommandHandler<Void> {

	/*package*/ EditVoteHandler(final Bot bot) {
		super(bot, "ed-vo", "edit-vote");
	}

	@Override
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
	}
}

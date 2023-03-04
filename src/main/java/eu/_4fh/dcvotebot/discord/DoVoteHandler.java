package eu._4fh.dcvotebot.discord;

import java.time.Instant;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.Validate;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.dcvotebot.db.Db;
import eu._4fh.dcvotebot.db.Db.LockHolder;
import eu._4fh.dcvotebot.db.Vote;
import eu._4fh.dcvotebot.db.VoteOption;
import eu._4fh.dcvotebot.discord.DoVoteHandler.VoteData;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

@DefaultAnnotation(NonNull.class)
public class DoVoteHandler extends AbstractCommandHandler<VoteData> {
	/*package*/ static class VoteData {
		private final long voteId;
		private final BitSet votes = new BitSet(Vote.MAX_VOTE_OPTIONS);

		private VoteData(final long voteId) {
			this.voteId = voteId;
		}
	}

	private static final String SEND_VOTE_BUTTON_PREFIX = "sendBtn";
	private static final String SELECT_VOTES_PREFIX = "votesSel";

	/*package*/ DoVoteHandler(final Bot bot) {
		super(bot, "do-vo", null);
	}

	@Override
	protected SlashCommandData createCommandData() {
		return null;
	}

	private void handleVote(Db db, ButtonInteractionEvent event, VoteData data, Vote vote) {
		final long voterId = event.getUser().getIdLong();
		vote = vote.copy();
		boolean hasAlreadyVoted = false;
		int votesSet = 0;
		for (int i = 0; i < vote.options.size(); ++i) {
			final VoteOption option = vote.options.get(i);
			hasAlreadyVoted |= option.voters.contains(voterId);
			if (data.votes.get(i)) {
				vote.options.get(i).voters.add(voterId);
				votesSet++;
			} else {
				vote.options.get(i).voters.remove(voterId);
			}
		}

		final Instant voteEnd = vote.start.plus(vote.settings.duration);
		if (Instant.now().isAfter(voteEnd)) {
			event.getHook().editOriginal("This vote already ended.").setComponents(Collections.emptyList()).queue();
		} else if (hasAlreadyVoted && !vote.settings.canChangeAnswers) {
			event.getHook().editOriginal("You already voted and can't change your answer.")
					.setComponents(Collections.emptyList()).queue();
		} else if (votesSet <= 0) {
			event.getHook().editOriginal("You didn't select any vote option.").setComponents(Collections.emptyList())
					.queue();
		} else if (votesSet > vote.settings.answersPerUser) {
			event.getHook().editOriginal("You selected more than " + vote.settings.answersPerUser + " options.")
					.setComponents(Collections.emptyList()).queue();
		} else {
			try (LockHolder lock = db.getLock(event.getGuild().getIdLong())) {
				db.setVote(lock, data.voteId, vote);
			}
			event.getHook()
					.editOriginal("Saved your vote. It could take some minutes until the vote-message shows your vote.")
					.setComponents(Collections.emptyList()).queue();
		}
	}

	private void handleSendVoteButton(final String cacheId, final ButtonInteractionEvent event) {
		final VoteData data = getCacheObject(cacheId);
		if (data == null) {
			event.editMessage("Your vote timed out. Please dismiss this message and click vote again.")
					.setComponents(Collections.emptyList()).queue();
			return;
		}

		event.deferEdit().queue();
		final Db db = Db.instance();
		try (LockHolder lock = db.getLock(event.getGuild().getIdLong())) {
			final Vote vote = db.getVote(lock, data.voteId);
			Validate.notNull(vote);
			handleVote(db, event, data, vote);
		}
	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		if (!handlesEvent(event)) {
			return;
		}

		final String id = event.getComponentId();
		if (isComponent(SEND_VOTE_BUTTON_PREFIX, id)) {
			final String cacheId = getComponentDataFromId(SEND_VOTE_BUTTON_PREFIX, id);
			handleSendVoteButton(cacheId, event);
		} else {
			throw new IllegalStateException("Cant handle button " + event.getComponentId());
		}
	}

	@Override
	public void onStringSelectInteraction(StringSelectInteractionEvent event) {
		if (!handlesEvent(event)) {
			return;
		}

		final String cacheId = getComponentDataFromId(SELECT_VOTES_PREFIX, event.getComponentId());
		final VoteData data = getCacheObject(cacheId);
		if (data == null) {
			event.editMessage("Sorry, something went wrong. Please try to vote again.")
					.setComponents(Collections.emptyList()).queue();
			return;
		}

		data.votes.clear();
		for (final SelectOption option : event.getSelectedOptions()) {
			data.votes.set(Integer.parseInt(option.getValue()));
		}

		event.deferEdit().queue();
	}

	/*package*/ void startVote(final long voteId, final ButtonInteractionEvent event) {
		event.deferReply(true).queue();

		final Db db = Db.instance();
		final Vote vote;
		try (LockHolder lock = db.getLock(event.getGuild().getIdLong())) {
			vote = db.getVote(lock, voteId);
		}
		if (vote == null) {
			event.getHook().sendMessage("This vote is already deleted from the bot.").queue();
			return;
		}

		final VoteData data = new VoteData(voteId);
		final String cacheId = addCacheObject(data);
		final long voterId = event.getUser().getIdLong();
		final List<VoteOption> options = vote.options;
		final StringSelectMenu.Builder menuBuilder = StringSelectMenu
				.create(generateComponentId(SELECT_VOTES_PREFIX, cacheId));
		for (int i = 0; i < options.size(); ++i) {
			final VoteOption option = options.get(i);
			final SelectOption menuOption = SelectOption.of(option.name, Integer.toString(i))
					.withDefault(option.voters.contains(voterId));
			menuBuilder.addOptions(menuOption);
		}
		menuBuilder.setMinValues(1).setMaxValues(vote.settings.answersPerUser);

		final Button sendButton = Button.primary(generateComponentId(SEND_VOTE_BUTTON_PREFIX, cacheId), "Send vote");

		event.getHook().sendMessage("Select your vote").addActionRow(menuBuilder.build()).addActionRow(sendButton)
				.queue();
	}
}

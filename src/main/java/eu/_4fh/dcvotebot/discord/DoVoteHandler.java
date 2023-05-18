package eu._4fh.dcvotebot.discord;

import java.time.Instant;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.dcvotebot.db.Db;
import eu._4fh.dcvotebot.db.Db.NotFoundException;
import eu._4fh.dcvotebot.db.Db.Transaction;
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
	/*package for test*/ static class VoteData {
		private final long voteId;
		/*package for test*/ final BitSet votes = new BitSet(Vote.MAX_VOTE_OPTIONS);

		/*package for test*/ VoteData(final long voteId) {
			this.voteId = voteId;
		}
	}

	/*package for test*/ static final String SEND_VOTE_BUTTON_PREFIX = "answerSendBtn";
	/*package for test*/ static final String SELECT_VOTES_PREFIX = "answersSel";

	/*package*/ DoVoteHandler(final Bot bot) {
		super(bot, "do-po", null);
	}

	@Override
	protected SlashCommandData createCommandData() {
		return null;
	}

	/*package for test*/ void handleVote(Db db, ButtonInteractionEvent event, VoteData data, Vote vote) {
		final long voterId = event.getUser().getIdLong();
		final Set<Long> votersVotes = new HashSet<>();
		boolean hasAlreadyVoted = false;
		int votesSet = 0;
		for (int i = 0; i < vote.options.size(); ++i) {
			final VoteOption option = vote.options.get(i);
			hasAlreadyVoted |= option.voters.contains(voterId);
			if (data.votes.get(i)) {
				votersVotes.add(option.id);
				votesSet++;
			}
		}

		final Instant voteEnd = vote.start.plus(vote.settings.duration);
		if (Instant.now().isAfter(voteEnd)) {
			event.getHook().editOriginal("This poll already ended.").setComponents(Collections.emptyList()).queue();
		} else if (hasAlreadyVoted && !vote.settings.canChangeAnswers) {
			event.getHook().editOriginal("You already voted and can't change your answer.")
					.setComponents(Collections.emptyList()).queue();
		} else if (votesSet <= 0) {
			event.getHook().editOriginal("You didn't select any answer.").setComponents(Collections.emptyList())
					.queue();
		} else if (votesSet > vote.settings.answersPerUser) {
			event.getHook().editOriginal("You selected more than " + vote.settings.answersPerUser + " answers.")
					.setComponents(Collections.emptyList()).queue();
		} else {
			try (Transaction trans = db.getTransaction(event.getGuild().getIdLong())) {
				db.updateVoteVotes(trans, voterId, data.voteId, votersVotes);
			}
			event.getHook()
					.editOriginal(
							"Saved your answer. It could take some minutes until the poll-message shows your answers.")
					.setComponents(Collections.emptyList()).queue();
			bot.updateVoteText(event.getGuild().getIdLong(), data.voteId);
		}
	}

	/*package for test*/ void forTestHandleSendVoteButton(final long userId, final ButtonInteractionEvent event) {
		handleSendVoteButton(userId, event);
	}

	private void handleSendVoteButton(final long userId, final ButtonInteractionEvent event) {
		final VoteData data = getCacheObject(userId);
		if (data == null) {
			event.editMessage("Your answer timed out. Please dismiss this message and click Answer again.")
					.setComponents(Collections.emptyList()).queue();
			return;
		}

		event.deferEdit().queue();
		final Db db = Db.instance();
		try (Transaction trans = db.getTransaction(event.getGuild().getIdLong())) {
			final Vote vote = db.getVote(trans, data.voteId);
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
			handleSendVoteButton(event.getUser().getIdLong(), event);
		} else {
			throw new IllegalStateException("Cant handle button " + id);
		}
	}

	@Override
	public void onStringSelectInteraction(StringSelectInteractionEvent event) {
		if (!handlesEvent(event)) {
			return;
		}

		final VoteData data = getCacheObject(event.getUser().getIdLong());
		if (data == null) {
			event.editMessage("Sorry, something went wrong. Please try to answer again.")
					.setComponents(Collections.emptyList()).queue();
			return;
		}

		data.votes.clear();
		for (final SelectOption option : event.getSelectedOptions()) {
			data.votes.set(Integer.parseInt(option.getValue()));
		}

		event.deferEdit().queue();
	}

	/*package*/ void startVote(final ButtonInteractionEvent event) {
		event.deferReply(true).queue();

		final Db db = Db.instance();
		final long voteId = event.getMessageIdLong();
		final Vote vote;
		try (Transaction trans = db.getTransaction(event.getGuild().getIdLong())) {
			vote = db.getVote(trans, voteId);
		} catch (NotFoundException e) {
			event.getHook().sendMessage("This poll is already deleted from the bot.").queue();
			return;
		}

		final long voterId = event.getUser().getIdLong();
		final VoteData data = new VoteData(voteId);
		addCacheObject(voterId, data);
		final List<VoteOption> options = vote.options;
		final StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(generateComponentId(SELECT_VOTES_PREFIX));
		for (int i = 0; i < options.size(); ++i) {
			final VoteOption option = options.get(i);
			final SelectOption menuOption = SelectOption.of(option.name, Integer.toString(i))
					.withDefault(option.voters.contains(voterId));
			menuBuilder.addOptions(menuOption);
		}
		menuBuilder.setMinValues(1).setMaxValues(vote.settings.answersPerUser);

		final Button sendButton = Button.primary(generateComponentId(SEND_VOTE_BUTTON_PREFIX), "Send answer");

		event.getHook().sendMessage("Select your answer").addActionRow(menuBuilder.build()).addActionRow(sendButton)
				.queue();
	}
}

package eu._4fh.dcvotebot.discord;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.dcvotebot.db.Db;
import eu._4fh.dcvotebot.db.Db.Transaction;
import eu._4fh.dcvotebot.db.Vote;
import eu._4fh.dcvotebot.db.VoteOption;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

@DefaultAnnotation(NonNull.class)
/*package*/ class CommandUtil {
	public static class OptionValueException extends Exception {
		private static final long serialVersionUID = 5070766808630439218L;

		public OptionValueException(final String msg) {
			super(msg);
		}
	}

	private CommandUtil() {
	}

	/*package*/ static void addCreateVoteOptions(final SlashCommandData commandData, final boolean createVote) {
		addOption(commandData, "title", "Title for your poll", createVote, 100);
		addOption(commandData, "description", "Description", createVote, 1000);
		addVoteSettingsOptions(commandData, false);
	}

	/*package*/ static void addVoteSettingsOptions(final SlashCommandData commandData, final boolean allRequired) {
		addOption(commandData, "duration", "Duration for your poll. Format like 1d 2h 3m", allRequired, 16);
		addBoolOption(commandData, "users-can-change-answer", "If a user can change his/her answer.", allRequired);
		commandData.addOptions(new OptionData(OptionType.INTEGER, "answers-per-user",
				"How many answers a single user can vote for", allRequired).setMinValue(1).setMaxValue(25));
	}

	private static void addOption(final SlashCommandData commandData, final String title, final String description,
			final boolean isRequired, final int maxLength) {
		final OptionData option = new OptionData(OptionType.STRING, title, description, isRequired);
		option.setMaxLength(maxLength);
		commandData.addOptions(option);
	}

	private static void addBoolOption(final SlashCommandData commandData, final String title, final String description,
			final boolean isRequired) {
		final OptionData option = new OptionData(OptionType.STRING, title, description, isRequired)
				.addChoice("Yes", "true").addChoice("No", "false");
		commandData.addOptions(option);
	}

	/*package*/ static String createVoteText(final Vote vote) {
		final StringBuilder result = new StringBuilder();
		final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
				.withZone(ZoneId.of(vote.settings.timezoneId));
		@SuppressWarnings("resource")
		final Formatter numberFormatter = new Formatter(result, Locale.ROOT);
		final int bulletCode = 0x2022;
		final int arrowCode = 0x2192;
		final Set<Long> voters = new HashSet<>();
		final long votes = vote.options.stream().filter(option -> {
			voters.addAll(option.voters);
			return true;
		}).collect(Collectors.summingLong(option -> option.voters.size()));

		result.append("**").append(vote.title).append("**\n\n").append(vote.description).append("\n\n");

		result.appendCodePoint(bulletCode).append(" From ");
		dateFormatter.formatTo(vote.start, result);
		result.append(" until ");
		dateFormatter.formatTo(vote.start.plus(vote.settings.duration), result);
		result.append("\n");

		result.appendCodePoint(bulletCode).append(" ");
		numberFormatter.format("%2d", vote.settings.answersPerUser);
		if (vote.settings.answersPerUser > 1) {
			result.append(" Answers ");
		} else {
			result.append(" Answer ");
		}
		result.append("per User\n");

		result.appendCodePoint(bulletCode).append(" You can ");
		if (!vote.settings.canChangeAnswers) {
			result.append("*not* ");
		}
		result.append("change your answer\n");

		result.appendCodePoint(bulletCode).append(" ");
		numberFormatter.format("%4d", voters.size());
		result.append(" Users\n");

		result.append("\n");

		result.append("```");
		for (int i = 0; i < vote.options.size(); ++i) {
			if (i > 0) {
				result.append("\n");
			}

			final VoteOption option = vote.options.get(i);
			numberFormatter.format("%2d", i + 1);
			result.append(". ");
			result.append(option.name);
			result.append(" ").appendCodePoint(arrowCode).append(" ");
			if (option.voters.isEmpty()) {
				result.append("  0 /   0%");
			} else {
				numberFormatter.format("%3d / %3d%%", option.voters.size(),
						Math.round((double) option.voters.size() / (double) votes * 100d));
			}
		}
		result.append("```");

		return result.toString();
	}

	private static final Pattern durationPattern = Pattern
			.compile("^\\s*(?:(?<d>\\d+)d)?\\s*(?:(?<h>\\d+)h)?\\s*(?:(?<m>\\d+)m)?\\s*$");

	/*package*/ static @CheckForNull Duration parseDuration(final @CheckForNull OptionMapping option)
			throws OptionValueException {
		if (option == null) {
			return null;
		}

		final String duration = option.getAsString();
		final Matcher match = durationPattern.matcher(duration);
		if (!match.matches()) {
			throw new OptionValueException("Invalid duration format.");
		}

		Duration result = Duration.ofSeconds(0);
		@CheckForNull
		String group;
		if ((group = match.group("d")) != null) {
			result = result.plusDays(Short.parseShort(group));
		}
		if ((group = match.group("h")) != null) {
			result = result.plusHours(Short.parseShort(group));
		}
		if ((group = match.group("m")) != null) {
			result = result.plusMinutes(Short.parseShort(group));
		}

		if (result.toMinutes() <= 0 || result.toDays() > 366) {
			throw new OptionValueException("Duration is either less than 1 minute or more than 366 Days.");
		}
		return result;
	}

	/*package*/ static @CheckForNull Byte parseByte(final OptionMapping option, byte minValue, byte maxValue)
			throws OptionValueException {
		if (option == null) {
			return null;
		}

		final long value = option.getAsLong();
		if (value < minValue || value > maxValue) {
			throw new OptionValueException(
					"Option " + option.getName() + " is either less than " + minValue + " or more than " + maxValue);
		}
		return (byte) value;
	}

	/*package*/ static @CheckForNull Boolean parseBoolean(final OptionMapping option) {
		if (option == null) {
			return null;
		}
		return Boolean.valueOf(option.getAsString());
	}

	/*package*/ static void autoCompleteVotes(CommandAutoCompleteInteractionEvent event) {
		final Db db = Db.instance();
		try (Transaction trans = db.getTransaction(event.getGuild().getIdLong())) {
			final Collection<Long> allVoteIds = db.getAllServerVoteIds(trans);
			final List<Command.Choice> allChoices = new ArrayList<>(allVoteIds.size());
			allVoteIds.stream()
					.forEach(voteId -> allChoices.add(new Command.Choice(db.getVote(trans, voteId).title, voteId)));
			event.replyChoices(allChoices).queue();
		}
	}
}

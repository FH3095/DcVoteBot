package eu._4fh.dcvotebot.db;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.Validate;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

@DefaultAnnotation(NonNull.class)
public class Vote {
	public static final byte MAX_VOTE_OPTIONS = 25;

	public final VoteSettings settings;
	public final long channelId;
	public final String title;
	public final String description;
	public final Instant start;
	public final List<VoteOption> options;

	public static Vote create(final VoteSettings settings, final long channelId, final String title,
			final String description, final List<VoteOption> options) {
		return new Vote(settings, channelId, title, description, Instant.now().truncatedTo(ChronoUnit.SECONDS),
				Collections.unmodifiableList(new ArrayList<>(options)));
	}

	public static Vote createForTest(final VoteSettings settings, final long channelId, final String title,
			final String description, final List<VoteOption> options, final Instant start) {
		return new Vote(settings, channelId, title, description, start.truncatedTo(ChronoUnit.SECONDS),
				Collections.unmodifiableList(new ArrayList<>(options)));
	}

	private static <T> T nonNull(@CheckForNull T v1, T v2) {
		return v1 != null ? v1 : v2;
	}

	public static Vote createWithDefaults(final @CheckForNull VoteSettings settings, final @CheckForNull String title,
			final @CheckForNull String description, final Vote vote) {
		return new Vote(nonNull(settings, vote.settings), vote.channelId, nonNull(title, vote.title),
				nonNull(description, vote.description), vote.start, vote.options);
	}

	/*package*/ Vote(final VoteSettings settings, final long channelId, final String title, final String description,
			final Instant start, final List<VoteOption> options) {
		Validate.isTrue(channelId != 0);
		Validate.notBlank(title);
		Validate.notBlank(description);
		Validate.inclusiveBetween(1, MAX_VOTE_OPTIONS, options.size());
		this.settings = settings;
		this.channelId = channelId;
		this.title = title;
		this.description = description;
		this.start = start;
		this.options = options;
	}

	@Override
	public int hashCode() {
		return Objects.hash(channelId, description, options, settings, start, title);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof Vote)) {
			return false;
		}
		Vote other = (Vote) obj;
		return channelId == other.channelId && Objects.equals(description, other.description)
				&& Objects.equals(options, other.options) && Objects.equals(settings, other.settings)
				&& Objects.equals(start, other.start) && Objects.equals(title, other.title);
	}

	@Override
	public String toString() {
		return "Vote [settings=" + settings + ", channelId=" + channelId + ", title=" + title + ", description="
				+ description + ", start=" + start + ", options=" + options + "]";
	}
}

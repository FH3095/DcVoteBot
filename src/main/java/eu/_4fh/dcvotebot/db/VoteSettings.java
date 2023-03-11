package eu._4fh.dcvotebot.db;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

import org.apache.commons.lang3.Validate;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

@DefaultAnnotation(NonNull.class)
public class VoteSettings {
	public final byte answersPerUser;
	public final Duration duration;
	public final boolean canChangeAnswers;
	public final String timezoneId;

	public static VoteSettings getDefault() {
		return new VoteSettings((byte) 1, Duration.ofDays(1).toSeconds(), true, "UTC");
	}

	private static <T> T nonNull(@CheckForNull T v1, T v2) {
		return v1 != null ? v1 : v2;
	}

	public static VoteSettings createWithDefaults(final @CheckForNull Duration duration,
			final @CheckForNull Byte answersPerUser, final @CheckForNull Boolean canChangeAnswers,
			final VoteSettings defaultSettings) {
		return new VoteSettings(nonNull(answersPerUser, defaultSettings.answersPerUser),
				nonNull(duration, defaultSettings.duration).toSeconds(),
				nonNull(canChangeAnswers, defaultSettings.canChangeAnswers), defaultSettings.timezoneId);
	}

	public static VoteSettings create(final Duration duration, final byte votesPerUser,
			final boolean usersCanChangeAnswers, final ZoneId timezone) {
		return new VoteSettings(votesPerUser, duration.toSeconds(), usersCanChangeAnswers, timezone.getId());
	}

	/*package*/ VoteSettings(byte answersPerUser, long duration, boolean canChangeAnswers, final String timezoneId) {
		Validate.inclusiveBetween(1, Vote.MAX_VOTE_OPTIONS, answersPerUser);
		Validate.inclusiveBetween(1, Integer.MAX_VALUE, duration);
		Validate.notNull(timezoneId);
		this.answersPerUser = answersPerUser;
		this.duration = Duration.ofSeconds(duration);
		this.canChangeAnswers = canChangeAnswers;
		this.timezoneId = timezoneId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(answersPerUser, canChangeAnswers, duration, timezoneId);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof VoteSettings)) {
			return false;
		}
		VoteSettings other = (VoteSettings) obj;
		return answersPerUser == other.answersPerUser && canChangeAnswers == other.canChangeAnswers
				&& Objects.equals(duration, other.duration) && Objects.equals(timezoneId, other.timezoneId);
	}

	@Override
	public String toString() {
		return "VoteSettings [answersPerUser=" + answersPerUser + ", duration=" + duration + ", canChangeAnswers="
				+ canChangeAnswers + ", timezoneId=" + timezoneId + "]";
	}
}

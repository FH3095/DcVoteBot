package eu._4fh.dcvotebot.db;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Objects;

import org.apache.commons.lang3.Validate;
import org.json.JSONObject;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

@DefaultAnnotation(NonNull.class)
public class VoteSettings {
	public final int answersPerUser;
	public final Duration duration;
	public final boolean canChangeAnswers;
	public final String timezoneId;

	public static VoteSettings getDefault() {
		return new VoteSettings(1, Duration.ofDays(1).toSeconds(), true, "UTC");
	}

	private static <T> T nonNull(@CheckForNull T v1, T v2) {
		return v1 != null ? v1 : v2;
	}

	public static VoteSettings getWithDefaults(final @CheckForNull Duration duration,
			final @CheckForNull Integer answersPerUser, final @CheckForNull Boolean canChangeAnswers,
			final VoteSettings defaultSettings) {
		return new VoteSettings(nonNull(answersPerUser, defaultSettings.answersPerUser),
				nonNull(duration, defaultSettings.duration).toSeconds(),
				nonNull(canChangeAnswers, defaultSettings.canChangeAnswers), defaultSettings.timezoneId);
	}

	public static VoteSettings create(final Duration duration, final int votesPerUser,
			final boolean usersCanChangeAnswers, final ZoneId timezone) {
		return new VoteSettings(votesPerUser, duration.toSeconds(), usersCanChangeAnswers, timezone.getId());
	}

	/*package*/ VoteSettings(int answersPerUser, long duration, boolean canChangeAnswers, final String timezoneId) {
		Validate.inclusiveBetween(1, Integer.MAX_VALUE, answersPerUser);
		Validate.inclusiveBetween(1, Integer.MAX_VALUE, duration);
		this.answersPerUser = answersPerUser;
		this.duration = Duration.ofSeconds(duration);
		this.canChangeAnswers = canChangeAnswers;
		this.timezoneId = timezoneId;
	}

	/*package*/ JSONObject toJson() {
		final JSONObject obj = new JSONObject();
		obj.put("answersPerUser", answersPerUser);
		obj.put("duration", duration.toSeconds());
		obj.put("canChangeAnswers", canChangeAnswers);
		obj.put("timezoneId", timezoneId);
		return obj;
	}

	/*package*/ static VoteSettings fromJson(final JSONObject obj) {
		return new VoteSettings(obj.getInt("answersPerUser"), obj.getLong("duration"),
				obj.getBoolean("canChangeAnswers"), obj.getString("timezoneId"));
	}

	@Override
	public int hashCode() {
		return Objects.hash(answersPerUser, canChangeAnswers, duration);
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
				&& Objects.equals(duration, other.duration);
	}

	@Override
	public String toString() {
		return "VoteSettings [answersPerUser=" + answersPerUser + ", duration=" + duration + ", canChangeAnswers="
				+ canChangeAnswers + "]";
	}
}

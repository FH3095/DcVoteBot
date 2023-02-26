package eu._4fh.dcvotebot.db;

import java.time.Duration;
import java.util.Objects;

import org.apache.commons.lang3.Validate;
import org.json.JSONObject;

public class VoteSettings {
	public final int answersPerUser;
	public final Duration duration;
	public final boolean canChangeAnswers;

	public static VoteSettings getDefault() {
		return new VoteSettings(1, Duration.ofDays(1).toSeconds(), true);
	}

	/*package*/ VoteSettings(int answersPerUser, long duration, boolean canChangeAnswers) {
		Validate.inclusiveBetween(1, Integer.MAX_VALUE, answersPerUser);
		Validate.inclusiveBetween(1, Integer.MAX_VALUE, duration);
		this.answersPerUser = answersPerUser;
		this.duration = Duration.ofSeconds(duration);
		this.canChangeAnswers = canChangeAnswers;
	}

	/*package*/ JSONObject toJson() {
		final JSONObject obj = new JSONObject();
		obj.put("answersPerUser", answersPerUser);
		obj.put("duration", duration.toSeconds());
		obj.put("canChangeAnswers", canChangeAnswers);
		return obj;
	}

	/*package*/ static VoteSettings fromJson(final JSONObject obj) {
		return new VoteSettings(obj.getInt("answersPerUser"), obj.getLong("duration"),
				obj.getBoolean("canChangeAnswers"));
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

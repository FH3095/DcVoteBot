package eu._4fh.dcvotebot.db;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.Validate;
import org.json.JSONArray;
import org.json.JSONObject;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

@DefaultAnnotation(NonNull.class)
public class Vote {
	public static final short MAX_VOTE_OPTIONS = 25;

	public final VoteSettings settings;
	public final String title;
	public final String description;
	public final List<VoteOption> options;
	public final Instant start;

	public Vote(final VoteSettings settings, final String title, final String description,
			final List<VoteOption> options) {
		Validate.isTrue(!options.isEmpty());
		Validate.inclusiveBetween(1, MAX_VOTE_OPTIONS, options.size());
		this.settings = settings;
		this.title = title;
		this.description = description;
		this.options = Collections.unmodifiableList(new ArrayList<>(options));
		this.start = Instant.now().truncatedTo(ChronoUnit.SECONDS);
	}

	private Vote(final VoteSettings settings, final String title, final String description,
			final List<VoteOption> options, final Instant start) {
		Validate.isTrue(!options.isEmpty());
		this.settings = settings;
		this.title = title;
		this.description = description;
		this.options = options;
		this.start = start;
	}

	public Vote copy() {
		final List<VoteOption> options = this.options.stream().map(VoteOption::copy)
				.collect(Collectors.toUnmodifiableList());
		return new Vote(settings, title, description, options, start);
	}

	/*package*/ JSONObject toJson() {
		final JSONObject obj = new JSONObject();
		obj.put("settings", settings.toJson());
		obj.put("title", title);
		obj.put("description", description);
		final List<JSONObject> jsonOptions = options.stream().map(VoteOption::toJson).collect(Collectors.toList());
		obj.put("options", jsonOptions);
		obj.put("start", start.getEpochSecond());
		return obj;
	}

	/*package*/ static Vote fromJson(final JSONObject obj) {
		final JSONArray jsonOptions = obj.getJSONArray("options");
		final List<VoteOption> options = new ArrayList<>(jsonOptions.length());
		for (int i = 0; i < jsonOptions.length(); ++i) {
			options.add(VoteOption.fromJson(jsonOptions.getJSONObject(i)));
		}
		return new Vote(VoteSettings.fromJson(obj.getJSONObject("settings")), obj.getString("title"),
				obj.getString("description"), Collections.unmodifiableList(options),
				Instant.ofEpochSecond(obj.getLong("start")));
	}

	@Override
	public int hashCode() {
		return Objects.hash(description, options, settings, start, title);
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
		return Objects.equals(description, other.description) && Objects.equals(options, other.options)
				&& Objects.equals(settings, other.settings) && Objects.equals(start, other.start)
				&& Objects.equals(title, other.title);
	}

	@Override
	public String toString() {
		return "Vote [settings=" + settings + ", title=" + title + ", description=" + description + ", options="
				+ options + ", start=" + start + "]";
	}
}

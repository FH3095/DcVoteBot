package eu._4fh.dcvotebot.db;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

@DefaultAnnotation(NonNull.class)
public class VoteOption {
	public final String name;
	public final Set<Long> voters;

	public VoteOption(final String name) {
		this.name = name;
		voters = Collections.emptySet();
	}

	private VoteOption(final String name, final Set<Long> voters) {
		this.name = name;
		this.voters = voters;
	}

	public VoteOption copy() {
		return new VoteOption(name, new HashSet<>(voters));
	}

	/*package*/ JSONObject toJson() {
		final JSONObject obj = new JSONObject();
		obj.put("name", name);
		obj.put("voters", voters);
		return obj;
	}

	/*package*/ static VoteOption fromJson(final JSONObject obj) {
		final JSONArray jsonVoters = obj.getJSONArray("voters");
		final Set<Long> voters = new HashSet<>();
		for (int i = 0; i < jsonVoters.length(); ++i) {
			voters.add(jsonVoters.getLong(i));
		}
		return new VoteOption(obj.getString("name"), voters);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, voters);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof VoteOption)) {
			return false;
		}
		VoteOption other = (VoteOption) obj;
		return Objects.equals(name, other.name) && Objects.equals(voters, other.voters);
	}

	@Override
	public String toString() {
		return "VoteOption [name=" + name + ", voters=" + voters + "]";
	}
}

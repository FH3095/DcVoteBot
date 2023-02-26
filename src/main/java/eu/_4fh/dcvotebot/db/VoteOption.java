package eu._4fh.dcvotebot.db;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;

public class VoteOption {
	public final String name;
	public final Set<Long> voters;

	/*package*/ VoteOption(final String name) {
		this.name = name;
		voters = ConcurrentHashMap.newKeySet();
	}

	private VoteOption(final String name, final JSONArray voters) {
		this.name = name;
		this.voters = ConcurrentHashMap.newKeySet(voters.length());
		for (int i = 0; i < voters.length(); ++i) {
			this.voters.add(voters.getLong(i));
		}
	}

	/*package*/ JSONObject toJson() {
		final JSONObject obj = new JSONObject();
		obj.put("name", name);
		obj.put("voters", voters);
		return obj;
	}

	/*package*/ static VoteOption fromJson(final JSONObject obj) {
		return new VoteOption(obj.getString("name"), obj.getJSONArray("voters"));
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

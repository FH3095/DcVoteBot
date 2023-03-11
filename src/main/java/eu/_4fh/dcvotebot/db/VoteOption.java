package eu._4fh.dcvotebot.db;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;

@DefaultAnnotation(NonNull.class)
public class VoteOption {
	public final long id;
	public final String name;
	public final Set<Long> voters;

	public static VoteOption create(final String name) {
		return new VoteOption(0, name, Collections.emptySet());
	}

	/*package*/ VoteOption(final long id, final String name, final Set<Long> voters) {
		this.id = id;
		this.name = name;
		this.voters = voters;
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, name, voters);
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
		return id == other.id && Objects.equals(name, other.name) && Objects.equals(voters, other.voters);
	}

	@Override
	public String toString() {
		return "VoteOption [id=" + id + ", name=" + name + ", voters=" + voters + "]";
	}
}

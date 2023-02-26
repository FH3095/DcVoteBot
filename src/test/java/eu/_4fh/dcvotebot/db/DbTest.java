package eu._4fh.dcvotebot.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import eu._4fh.dcvotebot.db.Db.LockHolder;
import eu._4fh.dcvotebot.util.Config;
import eu._4fh.dcvotebot.util.TryAgainLaterException;

class DbTest {
	private Db db;

	@BeforeEach
	void before() {
		db = new Db();
	}

	@AfterEach
	void after() throws IOException {
		Files.walk(Config.instance().dbDataDir).filter(p -> Files.isRegularFile(p)).forEach(f -> {
			try {
				Files.delete(f);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	@Test
	@Timeout(value = 90, unit = TimeUnit.SECONDS)
	void testLockDifferentServers() throws InterruptedException, ExecutionException {
		final Consumer<Long> lockTask = serverId -> {
			try (final LockHolder lock = db.getLock(serverId)) {
				TimeUnit.SECONDS.sleep(Config.instance().dbLockWaitSeconds * 3);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
		};
		final Runnable r1 = () -> lockTask.accept(1L);
		final Runnable r2 = () -> lockTask.accept(2L);

		final ExecutorService es = Executors.newFixedThreadPool(2);
		final Future<?> t1 = es.submit(r1);
		final Future<?> t2 = es.submit(r2);
		assertThatNoException().isThrownBy(t1::get);
		assertThatNoException().isThrownBy(t2::get);
		es.shutdown();
	}

	@Test
	@Timeout(value = 90, unit = TimeUnit.SECONDS)
	void testLockSameServerWithException() throws InterruptedException {
		final Consumer<Long> lockTask = serverId -> {
			try (final LockHolder lock = db.getLock(serverId)) {
				TimeUnit.SECONDS.sleep(Config.instance().dbLockWaitSeconds * 3);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
		};
		final Runnable r1 = () -> lockTask.accept(1L);
		final Runnable r2 = () -> lockTask.accept(1L);

		final ExecutorService es = Executors.newFixedThreadPool(2);
		final Future<?> t1 = es.submit(r1);
		final Future<?> t2 = es.submit(r2);
		ExecutionException exception = null;
		try {
			t1.get();
		} catch (ExecutionException e) {
			exception = e;
		}
		try {
			t2.get();
		} catch (ExecutionException e) {
			assertThat(exception).isNull();
			exception = e;
		}
		es.shutdown();
		assertThat(exception).isNotNull().cause().isInstanceOf(TryAgainLaterException.class);
	}

	@Test
	@Timeout(value = 90, unit = TimeUnit.SECONDS)
	void testLockSameServerWithoutException() throws InterruptedException, ExecutionException {
		final Consumer<Long> lockTask = serverId -> {
			try (final LockHolder lock = db.getLock(serverId)) {
				TimeUnit.SECONDS.sleep(1);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
		};
		final Runnable r1 = () -> lockTask.accept(1L);
		final Runnable r2 = () -> lockTask.accept(1L);

		final Instant start = Instant.now();
		final ExecutorService es = Executors.newFixedThreadPool(2);
		final Future<?> t1 = es.submit(r1);
		final Future<?> t2 = es.submit(r2);
		assertThatNoException().isThrownBy(t1::get);
		assertThatNoException().isThrownBy(t2::get);
		es.shutdown();
		final Instant endMinus2Seconds = Instant.now().minusSeconds(2);
		assertThat(endMinus2Seconds).as("end minus 2 seconds must be greater or equal to start")
				.isAfterOrEqualTo(start);
	}

	@Test
	void readWriteReadSettings() {
		VoteSettings settings;
		try (LockHolder lock = db.getLock(1)) {
			settings = db.getDefaultSettings(lock);
		}
		assertThat(settings).isEqualTo(VoteSettings.getDefault());
		final VoteSettings newSettings = new VoteSettings(settings.answersPerUser, settings.duration.getSeconds(),
				!settings.canChangeAnswers);
		try (LockHolder lock = db.getLock(1)) {
			db.setDefaultSettings(lock, newSettings);
		}
		try (LockHolder lock = db.getLock(1)) {
			settings = db.getDefaultSettings(lock);
		}
		assertThat(settings).isEqualTo(newSettings);
		db.forTestResetCaches();
		try (LockHolder lock = db.getLock(1)) {
			settings = db.getDefaultSettings(lock);
		}
		assertThat(settings).isEqualTo(newSettings);
	}

	@Test
	void readVoteDifferentServerFails() {
		final Vote vote = new Vote(VoteSettings.getDefault(), "Title", "desc",
				Collections.singletonList(new VoteOption("Opt1")));
		try (LockHolder lock = db.getLock(1)) {
			db.setVote(lock, 1, vote);
		}
		try (LockHolder lock = db.getLock(2)) {
			assertThatThrownBy(() -> db.getVote(lock, 1)).isInstanceOf(CompletionException.class);
		}
	}

	@Test
	void readWriteReadVote() {
		final VoteOption opt1 = new VoteOption("Opt1");
		final VoteOption opt2 = new VoteOption("Opt2");
		opt1.voters.add(1L);
		opt2.voters.add(2L);
		opt2.voters.add(3L);
		final Vote newVote = new Vote(VoteSettings.getDefault(), "Title", "Desc", Arrays.asList(opt1, opt2));

		try (LockHolder lock = db.getLock(1L)) {
			db.setVote(lock, 1L, newVote);
		}

		Vote vote;
		try (LockHolder lock = db.getLock(1L)) {
			vote = db.getVote(lock, 1L);
		}
		assertThat(vote).isEqualTo(newVote);
		db.forTestResetCaches();
		try (LockHolder lock = db.getLock(1L)) {
			vote = db.getVote(lock, 1L);
		}
		assertThat(vote).isEqualTo(newVote);
	}

	@Test
	void testChangeVote() {
		Vote vote = new Vote(VoteSettings.getDefault(), "Title", "Desc",
				Collections.singletonList(new VoteOption("Opt1")));
		try (LockHolder lock = db.getLock(1)) {
			db.setVote(lock, 1, vote);
		}
		try (LockHolder lock = db.getLock(1)) {
			vote = db.getVote(lock, 1);
		}
		assertThat(vote.options.get(0).voters).isEmpty();
		vote.options.get(0).voters.add(1L);
		try (LockHolder lock = db.getLock(1)) {
			db.setVote(lock, 1, vote);
		}

		try (LockHolder lock = db.getLock(1)) {
			vote = db.getVote(lock, 1);
		}
		assertThat(vote.options.get(0).voters).containsExactlyInAnyOrder(1L);
		db.forTestResetCaches();
		try (LockHolder lock = db.getLock(1)) {
			vote = db.getVote(lock, 1);
		}
		assertThat(vote.options.get(0).voters).containsExactlyInAnyOrder(1L);
	}
}

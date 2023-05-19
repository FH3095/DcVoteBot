package eu._4fh.dcvotebot.discord;

import java.util.Collections;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.dcvotebot.util.Config;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

@DefaultAnnotation(NonNull.class)
public class Bot implements AutoCloseable {
	private final JDA jda;
	private final int shardId;
	private final DoVoteHandler doVoteHandler;
	private final VoteUpdateHandler voteUpdateHandler;

	public Bot(final int shardId, final int shardTotal) {
		this.shardId = shardId;
		voteUpdateHandler = new VoteUpdateHandler(this);

		doVoteHandler = new DoVoteHandler(this);
		final AbstractCommandHandler<?>[] commands = { new CreateVoteHandler(this), doVoteHandler,
				new EditVoteHandler(this), new VoteSettingsDefaultCommand(this),
				new StatsCommand(this, voteUpdateHandler) };

		final JDABuilder jdaBuilder = JDABuilder.createLight(Config.instance().discordToken, Collections.emptyList())
				.setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.NONE)
				.setChunkingFilter(ChunkingFilter.NONE).addEventListeners((Object[]) commands)
				.addEventListeners(voteUpdateHandler);
		if (shardTotal > 1) {
			jdaBuilder.useSharding(shardId, shardTotal);
		}
		jda = jdaBuilder.build();
		try {
			jda.awaitReady();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
		voteUpdateHandler.start();

		if (shardId == 0) {
			for (final AbstractCommandHandler<?> commandHandler : commands) {
				final CommandData commandData = commandHandler.createCommandData();
				if (commandData != null) {
					jda.upsertCommand(commandData).queue();
				}
			}
		}
	}

	@Override
	public void close() {
		if (voteUpdateHandler != null) {
			voteUpdateHandler.close();
		}
		if (jda != null) {
			jda.shutdown();
			try {
				jda.awaitShutdown();
			} catch (InterruptedException e) {
				// Ignore, shutting down anyway
				Thread.currentThread().interrupt();
			}
		}
	}

	/*package*/ void updateMessage(final long serverId, final long channelId, final long messageId, final String text) {
		jda.getGuildById(serverId).getTextChannelById(channelId).editMessageById(messageId, text).complete();
	}

	public long getShardId() {
		return shardId;
	}

	/*package*/ void handleStartVote(ButtonInteractionEvent event) {
		doVoteHandler.startVote(event);
	}

	/*package*/ void updateVoteText(long serverId, long voteId) {
		voteUpdateHandler.addToUpdateVote(serverId, voteId);
	}
}

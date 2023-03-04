package eu._4fh.dcvotebot.discord;

import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.dcvotebot.util.Config;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

@DefaultAnnotation(NonNull.class)
public class Bot extends ListenerAdapter implements AutoCloseable {
	private final JDA jda;
	private final DoVoteHandler doVoteHandler;

	public Bot(final int shardId, final int shardTotal) {
		doVoteHandler = new DoVoteHandler(this);
		final AbstractCommandHandler<?>[] commands = { new CreateVoteHandler(this), doVoteHandler,
				new EditVoteHandler(this), new DeleteVoteHandler(this), new VoteSettingsDefaultCommand(this) };

		final JDABuilder jdaBuilder = JDABuilder
				.createDefault(Config.instance().discordToken, GatewayIntent.GUILD_MESSAGE_REACTIONS)
				.disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
				.setAutoReconnect(true).setMemberCachePolicy(MemberCachePolicy.NONE)
				.setChunkingFilter(ChunkingFilter.NONE).addEventListeners((Object[]) commands);
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

	/*package*/ void handleStartVote(final long voteId, ButtonInteractionEvent event) {
		doVoteHandler.startVote(voteId, event);
	}

	/*package*/ void handleVoted(long serverId, long voteId) {
		// TODO Auto-generated method stub

	}
}

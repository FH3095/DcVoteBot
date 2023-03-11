package eu._4fh.dcvotebot.discord;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.Validate;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.DefaultAnnotation;
import edu.umd.cs.findbugs.annotations.NonNull;
import eu._4fh.dcvotebot.util.Config;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

@DefaultAnnotation(NonNull.class)
public abstract class AbstractCommandHandler<T> extends ListenerAdapter {
	private static final Cache<String, Object> objectCache = Caffeine.newBuilder()
			.expireAfterWrite(Config.instance().interactionTimeout, TimeUnit.MINUTES)
			.scheduler(Scheduler.systemScheduler()).build();

	protected static final String ID_SEPERATOR = "_";

	private volatile boolean isShuttingDown = false;
	protected final Bot bot;
	protected final @CheckForNull String command;
	protected final String idPrefix;

	protected AbstractCommandHandler(final Bot bot, final String idPrefix, final @CheckForNull String command) {
		Validate.notBlank(idPrefix);
		Validate.inclusiveBetween(1, 29, idPrefix.length());
		Validate.isTrue(!idPrefix.contains(ID_SEPERATOR), "idPrefix cant contain '" + ID_SEPERATOR + "'");
		this.idPrefix = idPrefix + ID_SEPERATOR;

		this.command = command != null ? command.toLowerCase(Locale.ROOT) : null;

		Validate.notNull(bot);
		this.bot = bot;
	}

	/*package*/ void startShutdown() {
		isShuttingDown = true;
	}

	protected boolean handlesCommand(final CommandInteractionPayload event) {
		return !isShuttingDown && event.getFullCommandName().toLowerCase(Locale.ROOT).equals(command);
	}

	protected boolean handlesEvent(final GenericComponentInteractionCreateEvent event) {
		return !isShuttingDown && event.getComponentId().startsWith(idPrefix);
	}

	protected boolean handlesEvent(final ModalInteractionEvent event) {
		return !isShuttingDown && event.getModalId().startsWith(idPrefix);
	}

	protected abstract @CheckForNull SlashCommandData createCommandData();

	protected String addCacheObject(final long userId, final T cacheObject) {
		final String id = idPrefix + Long.toUnsignedString(userId);
		objectCache.put(id, cacheObject);
		return id;
	}

	@SuppressWarnings("unchecked")
	protected @CheckForNull T getCacheObject(final long userId) {
		final String id = idPrefix + Long.toUnsignedString(userId);
		return (T) objectCache.getIfPresent(id);
	}

	protected boolean isComponent(final String subPrefix, final String componentId) {
		final String idPrefixAndSubPrefix = idPrefix + subPrefix;
		final String prefixWithData = idPrefixAndSubPrefix + ID_SEPERATOR;
		return componentId.startsWith(prefixWithData) || componentId.equals(idPrefixAndSubPrefix);
	}

	protected String generateComponentId(final String subPrefix) {
		return idPrefix + subPrefix;
	}
}

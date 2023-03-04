package eu._4fh.dcvotebot.discord;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
public abstract class AbstractCommandHandler<CacheObjectT> extends ListenerAdapter {
	private static final AtomicInteger idGenerator = new AtomicInteger(0);
	private static final Cache<String, Object> objectCache = Caffeine.newBuilder()
			.expireAfterWrite(Config.instance().interactionTimeout, TimeUnit.MINUTES)
			.scheduler(Scheduler.systemScheduler()).build();

	protected static final String ID_SEPERATOR = "_";

	protected final Bot bot;
	protected final @CheckForNull String command;
	protected final String idPrefix;

	protected AbstractCommandHandler(final Bot bot, final String idPrefix, final String command) {
		Validate.notBlank(idPrefix);
		Validate.inclusiveBetween(1, 29, idPrefix.length());
		Validate.isTrue(!idPrefix.contains(ID_SEPERATOR), "idPrefix cant contain '" + ID_SEPERATOR + "'");
		this.idPrefix = idPrefix + ID_SEPERATOR;

		this.command = command != null ? command.toLowerCase(Locale.ROOT) : null;

		Validate.notNull(bot);
		this.bot = bot;
	}

	protected boolean handlesCommand(final CommandInteractionPayload event) {
		return event.getFullCommandName().toLowerCase(Locale.ROOT).equals(command);
	}

	protected boolean handlesEvent(final GenericComponentInteractionCreateEvent event) {
		return event.getComponentId().startsWith(idPrefix);
	}

	protected boolean handlesEvent(final ModalInteractionEvent event) {
		return event.getModalId().startsWith(idPrefix);
	}

	protected abstract SlashCommandData createCommandData();

	protected String addCacheObject(final CacheObjectT cacheObject) {
		final String id = idPrefix + Integer.toUnsignedString(idGenerator.incrementAndGet());
		objectCache.put(id, cacheObject);
		return id;
	}

	protected void setCacheObject(final String id, final CacheObjectT cacheObject) {
		objectCache.put(id, cacheObject);
	}

	@SuppressWarnings("unchecked")
	protected @CheckForNull CacheObjectT getCacheObject(final String id) {
		return (CacheObjectT) objectCache.getIfPresent(id);
	}

	protected boolean isComponent(final String subPrefix, final String componentId) {
		final String totalPrefix = idPrefix + subPrefix + ID_SEPERATOR;
		return componentId.startsWith(totalPrefix);
	}

	protected String generateComponentId(final String subPrefix, final String data) {
		return idPrefix + subPrefix + ID_SEPERATOR + data;
	}

	protected String getComponentDataFromId(final String subPrefix, final String componentId) {
		final String totalPrefix = idPrefix + subPrefix + ID_SEPERATOR;
		if (!componentId.startsWith(totalPrefix)) {
			throw new IllegalArgumentException("componentId " + componentId + " does not contain " + totalPrefix);
		}
		return componentId.substring(totalPrefix.length());
	}
}

package org.ddd4j.aggregate;

import java.time.Instant;

import org.ddd4j.aggregate.domain.Reaction;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.Channels;
import org.ddd4j.infrastructure.channel.api.CommitListener;
import org.ddd4j.infrastructure.channel.api.ErrorListener;
import org.ddd4j.infrastructure.channel.spi.Committer;
import org.ddd4j.infrastructure.domain.ChannelRevisions;
import org.ddd4j.infrastructure.domain.header.HeaderKey;
import org.ddd4j.infrastructure.domain.header.Headers;
import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.infrastructure.domain.value.ChannelSpec;
import org.ddd4j.infrastructure.publisher.RevisionCallback;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.ServiceConfigurer;
import org.ddd4j.util.Type;
import org.ddd4j.util.Typed;
import org.ddd4j.util.value.Named;
import org.ddd4j.util.value.Value;
import org.ddd4j.value.versioned.Recorded;
import org.ddd4j.value.versioned.Revision;

public interface CommandHandler<ID extends Value<ID>, C, E> extends Named {

	class Configurer<ID extends Value<ID>, C, E> implements ServiceConfigurer.Registered<CommandHandler<ID, C, E>> {

		private static final HeaderKey<Revision> EXPECTED = HeaderKey.of("EXPCD", Revision::serialize, Revision::deserialize);

		@Override
		public void configure(Context context, CommandHandler<ID, C, E> handler) {
			Handle<ID, C, E> handle = handler.createHandle(context);
			ErrorListener error = handler.createErrorListener(context);
			RevisionCallback callback = handler.createRevisionCallback(context);

			DomainChannelNameFactory nameFactory = context.get(DomainChannelNameFactory.REF);
			ChannelSpec<ID, C> commandSpec = handler.commandSpec(nameFactory);
			ChannelSpec<ID, E> eventSpec = handler.eventSpec(nameFactory);
			ChannelSpec<ID, ?> errorSpec = handler.errorSpec(nameFactory);

			ChannelRevisions revisions = new ChannelRevisions();
			Channels channels = context.get(Channels.REF);
			Committer<ID, Reaction<E>> submitter = channels.createSubmitter(eventSpec, Reaction::serialize);
			CommitListener<ID, C> commit = (n, c) -> handle.apply(c).thenApply(r -> r.fold( //
					acc -> submitter.commit(
							Recorded.uncommitted(c.getKey(), acc.getEvents(), Headers.EMPTY, Instant.now(), c.getHeader(EXPECTED).get())),
					rej -> null));
			channels.subscribe(commandSpec, commit, error, callback);
		}
	}

	@FunctionalInterface
	interface Handle<ID extends Value<ID>, C, E> {

		Promise<Reaction<E>> apply(Recorded<ID, C> command);
	}

	default ChannelSpec<ID, C> commandSpec(DomainChannelNameFactory factory) {
		ChannelName name = factory.create(name(), DomainChannelNameFactory.COMMAND);
		return ChannelSpec.of(name, getIdentifierType(), getCommandType());
	}

	default ErrorListener createErrorListener(Context context) {
		return ErrorListener.FAIL;
	}

	Handle<ID, C, E> createHandle(Context context);

	RevisionCallback createRevisionCallback(Context context);

	default ChannelSpec<ID, ?> errorSpec(DomainChannelNameFactory factory) {
		ChannelName name = factory.create(name(), DomainChannelNameFactory.ERROR);
		return ChannelSpec.of(name, getIdentifierType(), null); // TODO
	}

	default ChannelSpec<ID, E> eventSpec(DomainChannelNameFactory factory) {
		ChannelName name = factory.create(name(), DomainChannelNameFactory.EVENT);
		return ChannelSpec.of(name, getIdentifierType(), getEventType());
	}

	default Type<C> getCommandType() {
		return Typed.resolve(this, CommandHandler.class, 1);
	}

	default Type<E> getEventType() {
		return Typed.resolve(this, CommandHandler.class, 2);
	}

	default Type<ID> getIdentifierType() {
		return Typed.resolve(this, CommandHandler.class, 0);
	}
}
package org.ddd4j.infrastructure.channel;

import java.time.ZonedDateTime;

import org.ddd4j.aggregate.Identifier;
import org.ddd4j.collection.Props;
import org.ddd4j.infrastructure.Promise;
import org.ddd4j.infrastructure.channel.util.SchemaCodec;
import org.ddd4j.infrastructure.ChannelName;
import org.ddd4j.io.WriteBuffer;
import org.ddd4j.repository.RepositoryDefinition;
import org.ddd4j.spi.Context;
import org.ddd4j.spi.TestProvisioning;
import org.ddd4j.value.versioned.Revision;
import org.ddd4j.value.versioned.Revisions;
import org.ddd4j.value.versioned.Uncommitted;
import org.junit.Before;
import org.junit.Test;

public class CommitterTest {

	private TestProvisioning provisioning;

	@Before
	public void init() {
		provisioning = new TestProvisioning();
	}

	@Test
	public void testSimpleCommit() {
		provisioning.withConfigurer(b -> b.bind(Committer.FACTORY)
				.toInstance(d -> a -> Promise.completed(a.committed(new Revision(0, 0), ZonedDateTime.now()))));
		Context context = provisioning.createContext(Props.EMTPY);
		Committer<Identifier, String> committer = context.get(Committer.FACTORY).create(new RepositoryDefinition<Identifier, String>() {

			@Override
			public ChannelName getChannelName() {
				return ChannelName.of("test");
			}
		}, context.get(SchemaCodec.FACTORY), context.get(WriteBuffer.FACTORY));
		committer.commit(new Uncommitted<>(new Identifier(), "xxxx", Revisions.NONE));
	}
}

package org.ddd4j.aggregate;

import org.ddd4j.infrastructure.domain.value.ChannelName;
import org.ddd4j.spi.Ref;
import org.ddd4j.util.Require;
import org.ddd4j.util.value.Value;
import org.ddd4j.value.config.ConfKey;

@FunctionalInterface
public interface DomainChannelNameFactory {

	class ChannelType extends Value.StringBased<ChannelType> {

		public ChannelType(String suffix) {
			super(suffix);
		}

		public ChannelName channelName(String name, String delimiter) {
			Require.nonEmpty(name);
			Require.nonNull(delimiter);
			return name.endsWith(delimiter + value()) ? ChannelName.of(name) : ChannelName.of(name + delimiter + value());
		}
	}

	ChannelType COMMAND = new ChannelType("cmd");
	ChannelType ERROR = new ChannelType("err");
	ChannelType EVENT = new ChannelType("evt");

	ConfKey<String> CONF_DELIMITER = ConfKey.ofString("delimiter", "-");
	Ref<DomainChannelNameFactory> REF = Ref.of(DomainChannelNameFactory.class, ctx -> {
		String delimiter = ctx.conf(CONF_DELIMITER);
		return (name, type) -> type.channelName(name, delimiter);
	});

	ChannelName create(String name, ChannelType type);
}
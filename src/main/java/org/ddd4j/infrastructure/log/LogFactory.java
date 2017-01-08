package org.ddd4j.infrastructure.log;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.io.buffer.ReadBuffer;
import org.ddd4j.value.versioned.Revisions;

public interface LogFactory {

	class LogBasedRevisionsCallback implements RevisionsCallback {

		private Log<?, ?> log;

		@Override
		public Revisions get() {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public void set(Revisions revisions) {
			// TODO Auto-generated method stub

		}
	}

	ResourceDescriptor REVISIONS = new ResourceDescriptor("log-revisions");

	default Log<ReadBuffer, ReadBuffer> createLog(ResourceDescriptor descriptor) {

		return createLog(descriptor, callback);
	};

	Log<ReadBuffer, ReadBuffer> createLog(ResourceDescriptor descriptor, RevisionsCallback callback);
}

package org.ddd4j.infrastructure.channel.file;

import org.ddd4j.infrastructure.ResourceDescriptor;
import org.ddd4j.infrastructure.channel.ColdChannelCallback;
import org.ddd4j.value.versioned.Revision;

public class FileColdCallback implements ColdChannelCallback {

	@Override
	public void closeChecked() throws Exception {
		// TODO Auto-generated method stub
	}

	@Override
	public void seek(ResourceDescriptor topic, Revision revision) {
		// TODO Auto-generated method stub
	}

	@Override
	public void unseek(ResourceDescriptor topic) {
		// TODO Auto-generated method stub
	}
}

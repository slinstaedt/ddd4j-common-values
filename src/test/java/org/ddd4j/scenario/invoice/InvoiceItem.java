package org.ddd4j.scenario.invoice;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class InvoiceItem {

	int id;
	@NonNull
	String description;
	long amount;
}

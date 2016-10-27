package org.ddd4j.scenario.invoice;

import java.time.LocalDate;

import lombok.NonNull;
import lombok.Value;

public interface InvoiceEvent {

	@Value
	class InvoiceCreated implements InvoiceEvent {
	}

	@Value
	class InvoiceRecipientChanged implements InvoiceEvent {

		@NonNull
		String recipient;
		boolean readyToSend;
	}

	@Value
	class InvoiceItemAdded implements InvoiceEvent {

		@NonNull
		InvoiceItem item;
		long totalAmount;
		boolean readyToSend;
	}

	@Value
	class InvoiceItemRemoved implements InvoiceEvent {

		@NonNull
		InvoiceItem item;
		long totalAmount;
		boolean readyToSend;
	}

	@Value
	class InvoiceSent implements InvoiceEvent {

		@NonNull
		LocalDate sentOn;
		@NonNull
		LocalDate paymentDueOn;
	}

	@Value
	class UnusedEvent implements InvoiceEvent {
	}
}

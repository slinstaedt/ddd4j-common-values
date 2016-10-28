package org.ddd4j.scenario.invoice;

import java.time.LocalDate;

import org.ddd4j.scenario.invoice.InvoiceCommand.SendInvoice;
import org.ddd4j.scenario.invoice.InvoiceEvent.InvoiceRecipientChanged;
import org.ddd4j.value.Nothing;
import org.ddd4j.value.behavior.Effect;

public class SpecialRecipientHandler {

	public Effect<SpecialRecipientHandler, Nothing> on(InvoiceRecipientChanged event) {
		return Effect.Entity.none(this) //
				.when(t -> event.getRecipient().equals("xxx"))
				.cause(null, new SendInvoice(LocalDate.now()));
	}
}

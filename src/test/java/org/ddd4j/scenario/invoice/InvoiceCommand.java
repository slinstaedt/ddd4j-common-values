package org.ddd4j.scenario.invoice;

import java.time.LocalDate;

import org.ddd4j.scenario.invoice.Invoice.DraftInvoice;
import org.ddd4j.scenario.invoice.InvoiceEvent.InvoiceCreated;
import org.ddd4j.value.behavior.Behavior;

import lombok.NonNull;
import lombok.Value;

public interface InvoiceCommand {

	@Value
	class CreateInvoice implements InvoiceCommand {

		@Override
		public Behavior<? extends Invoice> apply(Invoice invoice) {
			return Invoice.FACTORY.record(new InvoiceCreated());
		}
	}

	@Value
	class ChangeInvoiceRecipient implements InvoiceCommand {

		@NonNull
		String recipient;

		@Override
		public Behavior<? extends Invoice> applyDraft(DraftInvoice invoice) {
			return invoice.changeRecipient(recipient);
		}
	}

	@Value
	class AddInvoiceItem implements InvoiceCommand {

		@NonNull
		String description;
		long amount;

		@Override
		public Behavior<? extends Invoice> applyDraft(DraftInvoice invoice) {
			return invoice.addItem(description, amount);
		}
	}

	@Value
	class SendInvoice implements InvoiceCommand {

		@NonNull
		LocalDate sentOn;

		@Override
		public Behavior<? extends Invoice> applyDraft(DraftInvoice invoice) {
			return invoice.send(sentOn);
		}
	}

	default Behavior<? extends Invoice> applyDraft(DraftInvoice invoice) {
		return Behavior.reject("wrong state");
	}

	default Behavior<? extends Invoice> apply(Invoice invoice) {
		return Behavior.none(invoice).guard(DraftInvoice.class).map(this::applyDraft);
	}
}

package org.ddd4j.scenario.invoice;

import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Collectors;

import org.ddd4j.scenario.invoice.InvoiceEvent.InvoiceCreated;
import org.ddd4j.scenario.invoice.InvoiceEvent.InvoiceItemAdded;
import org.ddd4j.scenario.invoice.InvoiceEvent.InvoiceRecipientChanged;
import org.ddd4j.scenario.invoice.InvoiceEvent.InvoiceSent;
import org.ddd4j.value.behavior.Behavior;
import org.ddd4j.value.behavior.HandlerChain;
import org.ddd4j.value.behavior.HandlerChain.FactoryHandler;
import org.ddd4j.value.collection.Seq;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Wither;

public interface Invoice {

	@Value
	@Wither
	class DraftInvoice implements Invoice {

		int nextItemId;
		@NonNull
		Optional<String> recipient;
		@NonNull
		Seq<InvoiceItem> items;

		boolean readyToSend() {
			return recipient.isPresent() && items.isNotEmpty();
		}

		long totalAmount() {
			return items.fold().collect(Collectors.summingLong(InvoiceItem::getAmount));
		}

		public Behavior<DraftInvoice> changeRecipient(String recipient) {
			return Behavior.accept(this::recipientChanged, new InvoiceRecipientChanged(recipient, readyToSend()));
		}

		public Behavior<DraftInvoice> addItem(String description, long amount) {
			return Behavior.accept(this::itemAdded,
					new InvoiceItemAdded(new InvoiceItem(nextItemId, description, amount), totalAmount() + amount, readyToSend()));
		}

		public Behavior<SentInvoice> send(LocalDate sentOn) {
			return Behavior.none(this) //
					.guard(DraftInvoice::readyToSend, "not ready to send")
					.handle(DraftInvoice::sent, new InvoiceSent(sentOn, sentOn.plusDays(14)));
		}

		DraftInvoice recipientChanged(InvoiceRecipientChanged event) {
			return withRecipient(Optional.of(event.getRecipient()));
		}

		DraftInvoice itemAdded(InvoiceItemAdded event) {
			return withItems(items.append().entry(event.getItem()));
		}

		SentInvoice sent(InvoiceSent event) {
			return new SentInvoice(event.getSentOn(), event.getPaymentDueOn());
		}
	}

	@Value
	class SentInvoice implements Invoice {

		@NonNull
		LocalDate sentOn;
		@NonNull
		LocalDate paymentDueOn;
	}

	FactoryHandler<DraftInvoice, InvoiceCreated> FACTORY = new FactoryHandler<DraftInvoice, InvoiceCreated>() {

		@Override
		public DraftInvoice create(InvoiceCreated message) {
			return new DraftInvoice(1, Optional.empty(), Seq.empty());
		}
	};

	HandlerChain<Invoice> EVENT_HANDLER = HandlerChain.create(Invoice.class)
			.chainFactory(InvoiceCreated.class, FACTORY)
			.when(DraftInvoice.class, InvoiceRecipientChanged.class, DraftInvoice::recipientChanged)
			.when(DraftInvoice.class, InvoiceItemAdded.class, DraftInvoice::itemAdded)
			.when(DraftInvoice.class, InvoiceSent.class, DraftInvoice::sent)
			.failedOnUnhandled();

	default Behavior<Invoice> apply(InvoiceEvent event) {
		return EVENT_HANDLER.apply(this, event);
	}
}

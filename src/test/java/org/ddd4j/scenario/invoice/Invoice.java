package org.ddd4j.scenario.invoice;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.ddd4j.contract.Require;
import org.ddd4j.scenario.invoice.InvoiceEvent.InvoiceCreated;
import org.ddd4j.scenario.invoice.InvoiceEvent.InvoiceItemAdded;
import org.ddd4j.scenario.invoice.InvoiceEvent.InvoiceRecipientChanged;
import org.ddd4j.scenario.invoice.InvoiceEvent.InvoiceSent;
import org.ddd4j.value.behavior.Behavior;
import org.ddd4j.value.behavior.HandlerChain;
import org.ddd4j.value.behavior.HandlerChain.FactoryHandler;
import org.ddd4j.value.collection.Seq;
import org.ddd4j.value.versioned.Uncommitted;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Wither;

public interface Invoice {

	class InvoiceEntity implements Invoice {

		private int nextItemId;
		private String recipient;
		private List<InvoiceItem> items;
		private LocalDate sentOn;
		private LocalDate paymentDueOn;

		boolean readyToSend() {
			return recipient != null && !items.isEmpty();
		}

		long totalAmount() {
			return items.stream().collect(Collectors.summingLong(InvoiceItem::getAmount));
		}

		public Behavior<InvoiceEntity> changeRecipient(String recipient) {
			return Behavior.Entity.accept(this, InvoiceEntity::recipientChanged, new InvoiceRecipientChanged(recipient, readyToSend()));
		}

		public Behavior<InvoiceEntity> addItem(String description, long amount) {
			return Behavior.Entity.accept(this, InvoiceEntity::itemAdded,
					new InvoiceItemAdded(new InvoiceItem(nextItemId, description, amount), totalAmount() + amount, readyToSend()));
		}

		public Behavior<InvoiceEntity> send(LocalDate sentOn) {
			return Behavior.Entity.none(this) //
					.guard(InvoiceEntity::readyToSend, "not ready to send")
					.accept(InvoiceEntity::sent, new InvoiceSent(sentOn, sentOn.plusDays(14)));
		}

		void recipientChanged(InvoiceRecipientChanged event) {
			recipient = Require.nonEmpty(event.getRecipient());
		}

		void itemAdded(InvoiceItemAdded event) {
			items.add(event.getItem());
		}

		void sent(InvoiceSent event) {
			sentOn = event.getSentOn();
			paymentDueOn = event.getPaymentDueOn();
		}
	}

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
			return Behavior.Value.none(this) //
					.guard(DraftInvoice::readyToSend, "not ready to send")
					.accept(DraftInvoice::sent, new InvoiceSent(sentOn, sentOn.plusDays(14)));
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
			.chainReference(InvoiceEntity.class, InvoiceRecipientChanged.class, InvoiceEntity::recipientChanged)
			.chainReference(InvoiceEntity.class, InvoiceItemAdded.class, InvoiceEntity::itemAdded)
			.chainReference(InvoiceEntity.class, InvoiceSent.class, InvoiceEntity::sent)
			.failedOnUnhandled();

	default Behavior<? extends Invoice> apply(Uncommitted<? extends InvoiceEvent> event) {
		return EVENT_HANDLER.record(this, event);
	}
}

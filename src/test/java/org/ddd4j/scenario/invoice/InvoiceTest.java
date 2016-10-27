package org.ddd4j.scenario.invoice;

import org.ddd4j.scenario.invoice.Invoice.DraftInvoice;
import org.ddd4j.scenario.invoice.InvoiceEvent.InvoiceCreated;
import org.ddd4j.scenario.invoice.InvoiceEvent.InvoiceRecipientChanged;
import org.ddd4j.value.behavior.Behavior;
import org.junit.Assert;
import org.junit.Test;

public class InvoiceTest {

	@Test
	public void newInvoice() {
		DraftInvoice result = Invoice.FACTORY.create(new InvoiceCreated());

		Assert.assertNotNull(result);
		Assert.assertEquals("hallo", result.getRecipient().orElseGet(() -> "hallo"));
	}

	@Test
	public void multipleEvents() {
		Behavior<DraftInvoice> behavior = Invoice.FACTORY.create(new InvoiceCreated()).changeRecipient("1").map(t -> t.changeRecipient("2"));

		behavior.assertContain(new InvoiceRecipientChanged("1", false));
		behavior.assertContain(new InvoiceRecipientChanged("2", false));
	}
}

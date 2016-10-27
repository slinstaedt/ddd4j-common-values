package org.ddd4j.scenario.shipment.test;

import org.ddd4j.scenario.shipment.api.Location;
import org.ddd4j.scenario.shipment.domain.Shipment;
import org.ddd4j.value.behavior.Behavior;
import org.junit.Before;
import org.junit.Test;

public class ShipmentTest {

	private static final Location HAM = new Location();
	private static final Location NYC = new Location();

	private Shipment shipment;

	@Before
	public void setup() {
		shipment = new Shipment(HAM, NYC);
	}

	@Test
	public void arrive() {
		shipment.arrive();
	}

	@Test(expected = IllegalStateException.class)
	public void doubleArriveShouldFail() {
		Behavior.accept(shipment).map(Shipment::arrive).map(Shipment::arrive);
	}
}

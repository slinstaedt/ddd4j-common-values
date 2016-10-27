package org.ddd4j.scenario.shipment.domain;

import org.ddd4j.contract.Require;
import org.ddd4j.scenario.shipment.api.Location;
import org.ddd4j.scenario.shipment.api.ShipmentEvent;
import org.ddd4j.value.behavior.Behavior;
import org.ddd4j.value.behavior.HandlerChain;

public class Shipment {

	public static final HandlerChain<Shipment, ShipmentEvent> EVENT_HANDLER = HandlerChain.<Shipment, ShipmentEvent> unhandled()
			.chainFactory(ShipmentEvent.ShipmentPlanned.class, Shipment::new)
			.chainReference(ShipmentEvent.ShipmentDeparted.class, Shipment::on)
			.chainReference(ShipmentEvent.ShipmentArrived.class, Shipment::on);

	private final Location departure;
	private final Location arrival;

	private int state;

	public Shipment(Location departure, Location arrival) {
		this.departure = Require.nonNull(departure);
		this.arrival = Require.nonNull(arrival);
		state = 0;
	}

	Shipment(ShipmentEvent.ShipmentPlanned event) {
		this(event.getDeparture(), event.getArrival());
	}

	public Behavior<Shipment> depart() {
		return apply(new ShipmentEvent.ShipmentDeparted(departure));
	}

	public Behavior<Shipment> arrive() {
		return apply(new ShipmentEvent.ShipmentArrived(arrival));
	}

	Behavior<Shipment> apply(ShipmentEvent event) {
		return EVENT_HANDLER.applyBehavior(this, event);
	}

	void on(ShipmentEvent.ShipmentDeparted event) {
	}

	void on(ShipmentEvent.ShipmentArrived event) {
		Require.that(state++ == 1);
	}
}

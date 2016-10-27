package org.ddd4j.scenario.shipment.api;

import org.ddd4j.contract.Require;

public interface ShipmentEvent {

	class ShipmentPlanned implements ShipmentEvent {

		private final Location departure;
		private final Location arrival;

		public ShipmentPlanned(Location departure, Location arrival) {
			this.departure = Require.nonNull(departure);
			this.arrival = Require.nonNull(arrival);
		}

		public Location getArrival() {
			return arrival;
		}

		public Location getDeparture() {
			return departure;
		}
	}

	class ShipmentDeparted implements ShipmentEvent {

		private final Location location;

		public ShipmentDeparted(Location location) {
			this.location = Require.nonNull(location);
		}

		public Location getLocation() {
			return location;
		}
	}

	class ShipmentArrived implements ShipmentEvent {

		private final Location location;

		public ShipmentArrived(Location location) {
			this.location = Require.nonNull(location);
		}

		public Location getLocation() {
			return location;
		}
	}
}

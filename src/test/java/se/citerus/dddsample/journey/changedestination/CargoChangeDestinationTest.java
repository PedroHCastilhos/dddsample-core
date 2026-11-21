package se.citerus.dddsample.journey.changedestination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.citerus.dddsample.domain.model.cargo.Cargo;
import se.citerus.dddsample.domain.model.cargo.HandlingActivity;
import se.citerus.dddsample.domain.model.cargo.Itinerary;
import se.citerus.dddsample.domain.model.cargo.Leg;
import se.citerus.dddsample.domain.model.cargo.TrackingId;
import se.citerus.dddsample.domain.model.handling.HandlingEvent;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static se.citerus.dddsample.application.util.DateUtils.toDate;
import static se.citerus.dddsample.domain.model.cargo.RoutingStatus.*;
import static se.citerus.dddsample.domain.model.cargo.TransportStatus.*;
import static se.citerus.dddsample.domain.model.handling.HandlingEvent.Type.*;
import static se.citerus.dddsample.infrastructure.sampledata.SampleLocations.*;
import static se.citerus.dddsample.infrastructure.sampledata.SampleVoyages.*;
import static se.citerus.dddsample.journey.changedestination.JourneyFixtures.*;

class CargoChangeDestinationTest {

  private Cargo routedCargo() {
    return new Cargo(new TrackingId("UC"), spec(HONGKONG, STOCKHOLM, DEADLINE), originalItinerary());
  }

  private Cargo cargoWithoutItinerary() {
    return new Cargo(new TrackingId("UC"), spec(HONGKONG, STOCKHOLM, DEADLINE));
  }

  @Test
  @DisplayName("UC-01: new destination not served by the old itinerary => MISROUTED, no ETA, no next activity")
  void uc01_newDestinationMakesOldItineraryMisrouted() {
    Cargo cargo = routedCargo();
    assertThat(cargo.delivery().routingStatus()).isEqualTo(ROUTED);

    cargo.specifyNewRoute(spec(HONGKONG, HAMBURG, DEADLINE));

    assertThat(cargo.delivery().routingStatus()).isEqualTo(MISROUTED);
    assertThat(cargo.delivery().estimatedTimeOfArrival()).isNull();
    assertThat(cargo.delivery().nextExpectedActivity()).isNull();
  }

  @Test
  @DisplayName("UC-02: same destination with a later deadline => still ROUTED, ETA and next activity kept")
  void uc02_sameDestinationLaterDeadlineKeepsRoute() {
    Cargo cargo = routedCargo();

    cargo.specifyNewRoute(spec(HONGKONG, STOCKHOLM, DEADLINE.plusSeconds(24 * 3600)));

    assertThat(cargo.delivery().routingStatus()).isEqualTo(ROUTED);
    assertThat(cargo.delivery().estimatedTimeOfArrival()).isEqualTo(toDate("2009-03-14"));
    assertThat(cargo.delivery().nextExpectedActivity()).isEqualTo(new HandlingActivity(RECEIVE, HONGKONG));
  }

  @Test
  @DisplayName("UC-03a: deadline equal to the final arrival => MISROUTED (arrival must be strictly before)")
  void uc03a_deadlineEqualToArrivalIsNotSatisfied() {
    Cargo cargo = routedCargo();
    Instant arrival = toDate("2009-03-14");

    cargo.specifyNewRoute(spec(HONGKONG, STOCKHOLM, arrival));

    assertThat(cargo.delivery().routingStatus()).isEqualTo(MISROUTED);
  }

  @Test
  @DisplayName("UC-03b: deadline one second after the final arrival => ROUTED")
  void uc03b_deadlineOneSecondAfterArrivalIsSatisfied() {
    Cargo cargo = routedCargo();
    Instant arrival = toDate("2009-03-14");

    cargo.specifyNewRoute(spec(HONGKONG, STOCKHOLM, arrival.plusSeconds(1)));

    assertThat(cargo.delivery().routingStatus()).isEqualTo(ROUTED);
  }

  @Test
  @DisplayName("UC-03c: deadline one second before the final arrival => MISROUTED")
  void uc03c_deadlineOneSecondBeforeArrivalIsNotSatisfied() {
    Cargo cargo = routedCargo();
    Instant arrival = toDate("2009-03-14");

    cargo.specifyNewRoute(spec(HONGKONG, STOCKHOLM, arrival.minusSeconds(1)));

    assertThat(cargo.delivery().routingStatus()).isEqualTo(MISROUTED);
  }

  @Test
  @DisplayName("UC-04: cargo without itinerary and without events => NOT_ROUTED, NOT_RECEIVED, no ETA")
  void uc04_noItineraryNoEvents() {
    Cargo cargo = cargoWithoutItinerary();

    cargo.specifyNewRoute(spec(HONGKONG, HAMBURG, DEADLINE));

    assertThat(cargo.delivery().routingStatus()).isEqualTo(NOT_ROUTED);
    assertThat(cargo.delivery().transportStatus()).isEqualTo(NOT_RECEIVED);
    assertThat(cargo.delivery().estimatedTimeOfArrival()).isNull();
  }

  @Test
  @DisplayName("UC-05: received cargo without itinerary => IN_PORT and NOT_ROUTED (known defect D1)")
  void uc05_receivedCargoWithoutItineraryIsNotRouted() {
    Cargo cargo = cargoWithoutItinerary();
    HandlingEvent received = event(cargo, RECEIVE, HONGKONG, null, "2009-03-01");

    cargo.deriveDeliveryProgress(history(received));

    assertThat(cargo.delivery().transportStatus()).isEqualTo(IN_PORT);
    assertThat(cargo.delivery().routingStatus()).isEqualTo(NOT_ROUTED);
  }

  @Test
  @DisplayName("UC-06: changing destination of a received cargo without itinerary must not throw (known defect D2)")
  void uc06_changeDestinationOfReceivedCargoWithoutItinerary() {
    Cargo cargo = cargoWithoutItinerary();
    HandlingEvent received = event(cargo, RECEIVE, HONGKONG, null, "2009-03-01");
    cargo.deriveDeliveryProgress(history(received));

    assertThatCode(() -> cargo.specifyNewRoute(spec(HONGKONG, HAMBURG, DEADLINE)))
      .doesNotThrowAnyException();

    assertThat(cargo.delivery().routingStatus()).isEqualTo(NOT_ROUTED);
    assertThat(cargo.delivery().isMisdirected()).isFalse();
  }

  @Test
  @DisplayName("UC-07: cargo on board keeps its physical state when destination changes, and is ROUTED after re-routing")
  void uc07_physicalStateIsPreservedAcrossReRouting() {
    Cargo cargo = routedCargo();
    HandlingEvent received = event(cargo, RECEIVE, HONGKONG, null, "2009-03-01");
    HandlingEvent loaded = event(cargo, LOAD, HONGKONG, v100, "2009-03-03");
    cargo.deriveDeliveryProgress(history(received, loaded));
    assertThat(cargo.delivery().transportStatus()).isEqualTo(ONBOARD_CARRIER);

    cargo.specifyNewRoute(spec(HONGKONG, HAMBURG, DEADLINE));

    assertThat(cargo.delivery().routingStatus()).isEqualTo(MISROUTED);
    assertThat(cargo.delivery().transportStatus()).isEqualTo(ONBOARD_CARRIER);
    assertThat(cargo.delivery().currentVoyage()).isEqualTo(v100);
    assertThat(cargo.delivery().lastKnownLocation()).isEqualTo(HONGKONG);
    assertThat(cargo.delivery().isMisdirected()).isFalse();

    Itinerary newItinerary = hamburgItinerary();
    cargo.assignToRoute(newItinerary);

    assertThat(cargo.delivery().routingStatus()).isEqualTo(ROUTED);
    assertThat(cargo.delivery().estimatedTimeOfArrival()).isEqualTo(toDate("2009-03-12"));
    assertThat(cargo.delivery().nextExpectedActivity()).isEqualTo(new HandlingActivity(UNLOAD, TOKYO, v100));
    assertThat(cargo.delivery().isMisdirected()).isFalse();
  }

  @Test
  @DisplayName("UC-08: misdirected cargo re-routed from the current port is no longer misdirected (known defect D3)")
  void uc08_reRoutedCargoIsNoLongerMisdirected() {
    Cargo cargo = routedCargo();
    HandlingEvent received = event(cargo, RECEIVE, HONGKONG, null, "2009-03-01");
    HandlingEvent loaded = event(cargo, LOAD, HONGKONG, v100, "2009-03-03");
    HandlingEvent wrongUnload = event(cargo, UNLOAD, TOKYO, v100, "2009-03-05");
    cargo.deriveDeliveryProgress(history(received, loaded, wrongUnload));
    assertThat(cargo.delivery().isMisdirected()).isTrue();

    cargo.specifyNewRoute(spec(TOKYO, HAMBURG, DEADLINE));
    cargo.assignToRoute(new Itinerary(List.of(
      new Leg(v300, TOKYO, HAMBURG, toDate("2009-03-08"), toDate("2009-03-12"))
    )));

    assertThat(cargo.delivery().routingStatus()).isEqualTo(ROUTED);
    assertThat(cargo.delivery().isMisdirected()).isFalse();
  }
}

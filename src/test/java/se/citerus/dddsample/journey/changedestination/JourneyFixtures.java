package se.citerus.dddsample.journey.changedestination;

import se.citerus.dddsample.domain.model.cargo.*;
import se.citerus.dddsample.domain.model.handling.HandlingEvent;
import se.citerus.dddsample.domain.model.handling.HandlingHistory;
import se.citerus.dddsample.domain.model.location.Location;
import se.citerus.dddsample.domain.model.voyage.Voyage;

import java.time.Instant;
import java.util.List;

import static se.citerus.dddsample.application.util.DateUtils.toDate;
import static se.citerus.dddsample.infrastructure.sampledata.SampleLocations.*;
import static se.citerus.dddsample.infrastructure.sampledata.SampleVoyages.*;

final class JourneyFixtures {

  static final Instant DEADLINE = toDate("2009-03-18");

  static Itinerary originalItinerary() {
    return new Itinerary(List.of(
      new Leg(v100, HONGKONG, NEWYORK, toDate("2009-03-03"), toDate("2009-03-09")),
      new Leg(v200, NEWYORK, CHICAGO, toDate("2009-03-10"), toDate("2009-03-12")),
      new Leg(v200, CHICAGO, STOCKHOLM, toDate("2009-03-12"), toDate("2009-03-14"))
    ));
  }

  static Itinerary hamburgItinerary() {
    return new Itinerary(List.of(
      new Leg(v100, HONGKONG, TOKYO, toDate("2009-03-03"), toDate("2009-03-05")),
      new Leg(v300, TOKYO, HAMBURG, toDate("2009-03-08"), toDate("2009-03-12"))
    ));
  }

  static RouteSpecification spec(Location origin, Location destination, Instant deadline) {
    return new RouteSpecification(origin, destination, deadline);
  }

  static HandlingEvent event(Cargo cargo, HandlingEvent.Type type, Location location, Voyage voyage, String completion) {
    Instant completed = toDate(completion);
    return voyage == null
      ? new HandlingEvent(cargo, completed, completed, type, location)
      : new HandlingEvent(cargo, completed, completed, type, location, voyage);
  }

  static HandlingHistory history(HandlingEvent... events) {
    return new HandlingHistory(List.of(events));
  }

  private JourneyFixtures() {
  }
}

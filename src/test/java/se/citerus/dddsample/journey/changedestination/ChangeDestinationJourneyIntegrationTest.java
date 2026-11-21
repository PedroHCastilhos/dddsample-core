package se.citerus.dddsample.journey.changedestination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.citerus.dddsample.application.impl.BookingServiceImpl;
import se.citerus.dddsample.application.impl.CargoInspectionServiceImpl;
import se.citerus.dddsample.application.impl.HandlingEventServiceImpl;
import se.citerus.dddsample.domain.model.cargo.*;
import se.citerus.dddsample.domain.model.handling.HandlingEvent;
import se.citerus.dddsample.domain.model.handling.HandlingEventFactory;
import se.citerus.dddsample.domain.model.voyage.Voyage;
import se.citerus.dddsample.domain.model.voyage.VoyageNumber;
import se.citerus.dddsample.domain.service.RoutingService;
import se.citerus.dddsample.infrastructure.persistence.inmemory.CargoRepositoryInMem;
import se.citerus.dddsample.infrastructure.persistence.inmemory.HandlingEventRepositoryInMem;
import se.citerus.dddsample.infrastructure.persistence.inmemory.LocationRepositoryInMem;
import se.citerus.dddsample.infrastructure.persistence.inmemory.VoyageRepositoryInMem;

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

class ChangeDestinationJourneyIntegrationTest {

  private CargoRepositoryInMem cargoRepository;
  private BookingServiceImpl bookingService;
  private HandlingEventServiceImpl handlingEventService;
  private RecordingApplicationEvents applicationEvents;

  @BeforeEach
  void setUp() {
    RoutingService routingService = spec -> {
      if (spec.destination().equals(HAMBURG)) {
        return List.of(hamburgItinerary());
      }
      return List.of(originalItinerary());
    };

    applicationEvents = new RecordingApplicationEvents();
    cargoRepository = new CargoRepositoryInMem();
    HandlingEventRepositoryInMem handlingEventRepository = new HandlingEventRepositoryInMem();
    LocationRepositoryInMem locationRepository = new LocationRepositoryInMem();
    VoyageRepositoryInMem voyageRepository = new VoyageRepositoryInMem();

    HandlingEventFactory handlingEventFactory =
      new HandlingEventFactory(cargoRepository, voyageRepository, locationRepository);
    CargoInspectionServiceImpl inspectionService =
      new CargoInspectionServiceImpl(applicationEvents, cargoRepository, handlingEventRepository);
    handlingEventService =
      new HandlingEventServiceImpl(handlingEventRepository, applicationEvents, handlingEventFactory);
    bookingService = new BookingServiceImpl(cargoRepository, locationRepository, routingService,
      new CargoFactory(locationRepository, cargoRepository));
    applicationEvents.setCargoInspectionService(inspectionService);
  }

  private TrackingId bookAndRouteHongkongToStockholm() {
    TrackingId id = bookingService.bookNewCargo(HONGKONG.unLocode(), STOCKHOLM.unLocode(), DEADLINE);
    bookingService.assignCargoToRoute(bookingService.requestPossibleRoutesForCargo(id).get(0), id);
    return id;
  }

  private void handle(TrackingId id, HandlingEvent.Type type, Voyage voyage, se.citerus.dddsample.domain.model.location.Location location, String date)
    throws Exception {
    VoyageNumber number = voyage == null ? null : voyage.voyageNumber();
    handlingEventService.registerHandlingEvent(toDate(date), id, number, location.unLocode(), type);
  }

  @Test
  @DisplayName("IT-01: full journey - destination changed while on board, re-routed, delivered and claimed")
  void it01_fullJourney() throws Exception {
    TrackingId id = bookAndRouteHongkongToStockholm();
    Cargo cargo = cargoRepository.find(id);

    handle(id, RECEIVE, null, HONGKONG, "2009-03-01");
    handle(id, LOAD, v100, HONGKONG, "2009-03-03");
    assertThat(cargo.delivery().transportStatus()).isEqualTo(ONBOARD_CARRIER);
    assertThat(cargo.delivery().routingStatus()).isEqualTo(ROUTED);

    // Customer changes the destination while the cargo is on board
    bookingService.changeDestination(id, HAMBURG.unLocode());

    assertThat(cargo.routeSpecification().destination()).isEqualTo(HAMBURG);
    assertThat(cargo.delivery().routingStatus()).isEqualTo(MISROUTED);
    assertThat(cargo.delivery().transportStatus()).isEqualTo(ONBOARD_CARRIER);
    assertThat(cargo.delivery().currentVoyage()).isEqualTo(v100);
    assertThat(cargo.delivery().isMisdirected()).isFalse();
    assertThat(cargo.delivery().estimatedTimeOfArrival()).isNull();
    assertThat(cargo.delivery().nextExpectedActivity()).isNull();

    Itinerary newRoute = bookingService.requestPossibleRoutesForCargo(id).get(0);
    bookingService.assignCargoToRoute(newRoute, id);

    assertThat(cargo.delivery().routingStatus()).isEqualTo(ROUTED);
    assertThat(cargo.delivery().estimatedTimeOfArrival()).isEqualTo(toDate("2009-03-12"));
    assertThat(cargo.delivery().nextExpectedActivity()).isEqualTo(new HandlingActivity(UNLOAD, TOKYO, v100));

    handle(id, UNLOAD, v100, TOKYO, "2009-03-05");
    assertThat(cargo.delivery().isMisdirected()).isFalse();
    assertThat(cargo.delivery().nextExpectedActivity()).isEqualTo(new HandlingActivity(LOAD, TOKYO, v300));

    handle(id, LOAD, v300, TOKYO, "2009-03-08");
    assertThat(cargo.delivery().isMisdirected()).isFalse();
    assertThat(cargo.delivery().nextExpectedActivity()).isEqualTo(new HandlingActivity(UNLOAD, HAMBURG, v300));

    handle(id, UNLOAD, v300, HAMBURG, "2009-03-12");
    assertThat(cargo.delivery().isMisdirected()).isFalse();
    assertThat(cargo.delivery().isUnloadedAtDestination()).isTrue();
    assertThat(cargo.delivery().nextExpectedActivity()).isEqualTo(new HandlingActivity(CLAIM, HAMBURG));

    handle(id, CLAIM, null, HAMBURG, "2009-03-13");
    assertThat(cargo.delivery().transportStatus()).isEqualTo(CLAIMED);
    assertThat(cargo.delivery().nextExpectedActivity()).isNull();

    assertThat(applicationEvents.misdirectedNotifications).isZero();
    assertThat(applicationEvents.arrivedNotifications).isEqualTo(1);
  }

  @Test
  @DisplayName("IT-02: changing the destination and changing back restores the original plan")
  void it02_changeAndChangeBack() throws Exception {
    TrackingId id = bookAndRouteHongkongToStockholm();
    Cargo cargo = cargoRepository.find(id);
    handle(id, RECEIVE, null, HONGKONG, "2009-03-01");

    var originalEta = cargo.delivery().estimatedTimeOfArrival();
    var originalNext = cargo.delivery().nextExpectedActivity();
    assertThat(originalEta).isEqualTo(toDate("2009-03-14"));
    assertThat(originalNext).isEqualTo(new HandlingActivity(LOAD, HONGKONG, v100));

    bookingService.changeDestination(id, HAMBURG.unLocode());
    assertThat(cargo.delivery().routingStatus()).isEqualTo(MISROUTED);

    bookingService.changeDestination(id, STOCKHOLM.unLocode());
    assertThat(cargo.delivery().routingStatus()).isEqualTo(ROUTED);
    assertThat(cargo.delivery().estimatedTimeOfArrival()).isEqualTo(originalEta);
    assertThat(cargo.delivery().nextExpectedActivity()).isEqualTo(originalNext);
  }

  @Test
  @DisplayName("IT-03: cargo received before being routed stays NOT_ROUTED (known defect D1)")
  void it03_receivedBeforeRouting() throws Exception {
    TrackingId id = bookingService.bookNewCargo(HONGKONG.unLocode(), STOCKHOLM.unLocode(), DEADLINE);
    Cargo cargo = cargoRepository.find(id);

    handle(id, RECEIVE, null, HONGKONG, "2009-03-01");

    assertThat(cargo.delivery().transportStatus()).isEqualTo(IN_PORT);
    assertThat(cargo.delivery().routingStatus()).isEqualTo(NOT_ROUTED);
  }

  @Test
  @DisplayName("IT-04: destination of a received cargo without itinerary can be changed (known defect D2)")
  void it04_changeDestinationOfReceivedCargoWithoutItinerary() throws Exception {
    TrackingId id = bookingService.bookNewCargo(HONGKONG.unLocode(), STOCKHOLM.unLocode(), DEADLINE);
    Cargo cargo = cargoRepository.find(id);
    handle(id, RECEIVE, null, HONGKONG, "2009-03-01");

    assertThatCode(() -> bookingService.changeDestination(id, HAMBURG.unLocode()))
      .doesNotThrowAnyException();

    assertThat(cargo.routeSpecification().destination()).isEqualTo(HAMBURG);
    assertThat(cargo.delivery().routingStatus()).isEqualTo(NOT_ROUTED);
  }
}

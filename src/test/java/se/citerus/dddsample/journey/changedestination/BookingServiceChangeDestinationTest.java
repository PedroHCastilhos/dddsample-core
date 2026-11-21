package se.citerus.dddsample.journey.changedestination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import se.citerus.dddsample.application.impl.BookingServiceImpl;
import se.citerus.dddsample.domain.model.cargo.*;
import se.citerus.dddsample.domain.model.location.LocationRepository;
import se.citerus.dddsample.domain.model.location.UnLocode;
import se.citerus.dddsample.domain.service.RoutingService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static se.citerus.dddsample.infrastructure.sampledata.SampleLocations.*;
import static se.citerus.dddsample.journey.changedestination.JourneyFixtures.*;

class BookingServiceChangeDestinationTest {

  private static final TrackingId ID = new TrackingId("SV1");

  private CargoRepository cargoRepository;
  private LocationRepository locationRepository;
  private RoutingService routingService;
  private BookingServiceImpl bookingService;
  private Cargo cargo;

  @BeforeEach
  void setUp() {
    cargoRepository = mock(CargoRepository.class);
    locationRepository = mock(LocationRepository.class);
    routingService = mock(RoutingService.class);
    CargoFactory cargoFactory = new CargoFactory(locationRepository, cargoRepository);
    bookingService = new BookingServiceImpl(cargoRepository, locationRepository, routingService, cargoFactory);

    cargo = new Cargo(ID, spec(HONGKONG, STOCKHOLM, DEADLINE));
  }

  @Test
  @DisplayName("SV-01: existing cargo and destination => only destination changes, origin and deadline preserved")
  void sv01_changesOnlyTheDestination() {
    when(cargoRepository.find(ID)).thenReturn(cargo);
    when(locationRepository.find(HAMBURG.unLocode())).thenReturn(HAMBURG);

    bookingService.changeDestination(ID, HAMBURG.unLocode());

    assertThat(cargo.routeSpecification().destination()).isEqualTo(HAMBURG);
    assertThat(cargo.routeSpecification().origin()).isEqualTo(HONGKONG);
    assertThat(cargo.origin()).isEqualTo(HONGKONG);
    assertThat(cargo.routeSpecification().arrivalDeadline()).isEqualTo(DEADLINE);
    verify(cargoRepository, times(1)).store(cargo);
  }

  @Test
  @DisplayName("SV-02: non-existing cargo => IllegalArgumentException, nothing stored (known defect D4)")
  void sv02_nonExistingCargo() {
    when(cargoRepository.find(ID)).thenReturn(null);
    when(locationRepository.find(HAMBURG.unLocode())).thenReturn(HAMBURG);

    assertThatThrownBy(() -> bookingService.changeDestination(ID, HAMBURG.unLocode()))
      .isInstanceOf(IllegalArgumentException.class);

    verify(cargoRepository, never()).store(any(Cargo.class));
  }

  @Test
  @DisplayName("SV-03: unknown UN/LOCODE => runtime exception, nothing stored")
  void sv03_unknownLocation() {
    UnLocode unknown = new UnLocode("ZZZZZ");
    when(cargoRepository.find(ID)).thenReturn(cargo);
    when(locationRepository.find(unknown)).thenReturn(null);

    assertThatThrownBy(() -> bookingService.changeDestination(ID, unknown))
      .isInstanceOf(RuntimeException.class);

    verify(cargoRepository, never()).store(any(Cargo.class));
    assertThat(cargo.routeSpecification().destination()).isEqualTo(STOCKHOLM);
  }

  @Test
  @DisplayName("SV-04: destination equal to origin (RN2) => IllegalArgumentException, nothing stored")
  void sv04_destinationEqualToOrigin() {
    when(cargoRepository.find(ID)).thenReturn(cargo);
    when(locationRepository.find(HONGKONG.unLocode())).thenReturn(HONGKONG);

    assertThatThrownBy(() -> bookingService.changeDestination(ID, HONGKONG.unLocode()))
      .isInstanceOf(IllegalArgumentException.class);

    verify(cargoRepository, never()).store(any(Cargo.class));
    assertThat(cargo.routeSpecification().destination()).isEqualTo(STOCKHOLM);
  }

  @Test
  @DisplayName("SV-05: assigning a route to a non-existing cargo => IllegalArgumentException, nothing stored")
  void sv05_assignToNonExistingCargo() {
    when(cargoRepository.find(ID)).thenReturn(null);

    assertThatThrownBy(() -> bookingService.assignCargoToRoute(originalItinerary(), ID))
      .isInstanceOf(IllegalArgumentException.class);

    verify(cargoRepository, never()).store(any(Cargo.class));
  }

  @Test
  @DisplayName("SV-06: requesting routes for a non-existing cargo => empty list, routing service not called")
  void sv06_routesForNonExistingCargo() {
    when(cargoRepository.find(ID)).thenReturn(null);

    List<Itinerary> routes = bookingService.requestPossibleRoutesForCargo(ID);

    assertThat(routes).isEmpty();
    verifyNoInteractions(routingService);
  }

  @Test
  @DisplayName("SV-07: after changing destination, routing is requested for the new specification")
  void sv07_routingUsesNewSpecification() {
    when(cargoRepository.find(ID)).thenReturn(cargo);
    when(locationRepository.find(HAMBURG.unLocode())).thenReturn(HAMBURG);
    when(routingService.fetchRoutesForSpecification(any(RouteSpecification.class))).thenReturn(List.of());

    bookingService.changeDestination(ID, HAMBURG.unLocode());
    bookingService.requestPossibleRoutesForCargo(ID);

    ArgumentCaptor<RouteSpecification> captor = ArgumentCaptor.forClass(RouteSpecification.class);
    verify(routingService).fetchRoutesForSpecification(captor.capture());
    assertThat(captor.getValue().destination()).isEqualTo(HAMBURG);
    assertThat(captor.getValue().origin()).isEqualTo(HONGKONG);
    assertThat(captor.getValue().arrivalDeadline()).isEqualTo(DEADLINE);
  }
}

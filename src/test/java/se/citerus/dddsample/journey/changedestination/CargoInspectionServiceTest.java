package se.citerus.dddsample.journey.changedestination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.citerus.dddsample.application.ApplicationEvents;
import se.citerus.dddsample.application.impl.CargoInspectionServiceImpl;
import se.citerus.dddsample.domain.model.cargo.Cargo;
import se.citerus.dddsample.domain.model.cargo.CargoRepository;
import se.citerus.dddsample.domain.model.cargo.TrackingId;
import se.citerus.dddsample.domain.model.handling.HandlingEvent;
import se.citerus.dddsample.domain.model.handling.HandlingEventRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static se.citerus.dddsample.domain.model.handling.HandlingEvent.Type.*;
import static se.citerus.dddsample.infrastructure.sampledata.SampleLocations.*;
import static se.citerus.dddsample.infrastructure.sampledata.SampleVoyages.*;
import static se.citerus.dddsample.journey.changedestination.JourneyFixtures.*;

class CargoInspectionServiceTest {

  private static final TrackingId ID = new TrackingId("CI1");

  private ApplicationEvents applicationEvents;
  private CargoRepository cargoRepository;
  private HandlingEventRepository handlingEventRepository;
  private CargoInspectionServiceImpl service;

  @BeforeEach
  void setUp() {
    applicationEvents = mock(ApplicationEvents.class);
    cargoRepository = mock(CargoRepository.class);
    handlingEventRepository = mock(HandlingEventRepository.class);
    service = new CargoInspectionServiceImpl(applicationEvents, cargoRepository, handlingEventRepository);
  }

  private Cargo cargoToStockholm() {
    Cargo cargo = new Cargo(ID, spec(HONGKONG, STOCKHOLM, DEADLINE), originalItinerary());
    when(cargoRepository.find(ID)).thenReturn(cargo);
    return cargo;
  }

  private void givenHistory(HandlingEvent... events) {
    when(handlingEventRepository.lookupHandlingHistoryOfCargo(ID)).thenReturn(history(events));
  }

  @Test
  @DisplayName("CI-01: non-existing cargo => nothing notified, nothing persisted")
  void ci01_nonExistingCargo() {
    when(cargoRepository.find(ID)).thenReturn(null);

    service.inspectCargo(ID);

    verifyNoInteractions(applicationEvents);
    verify(cargoRepository, never()).store(any(Cargo.class));
  }

  @Test
  @DisplayName("CI-02: null tracking id => NullPointerException")
  void ci02_nullTrackingId() {
    assertThatThrownBy(() -> service.inspectCargo(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("CI-03: not misdirected, not arrived => no notification, cargo persisted")
  void ci03_neitherMisdirectedNorArrived() {
    Cargo cargo = cargoToStockholm();
    givenHistory(event(cargo, RECEIVE, HONGKONG, null, "2009-03-01"));

    service.inspectCargo(ID);

    assertThat(cargo.delivery().isMisdirected()).isFalse();
    assertThat(cargo.delivery().isUnloadedAtDestination()).isFalse();
    verify(applicationEvents, never()).cargoWasMisdirected(any(Cargo.class));
    verify(applicationEvents, never()).cargoHasArrived(any(Cargo.class));
    verify(cargoRepository, times(1)).store(cargo);
  }

  @Test
  @DisplayName("CI-04: misdirected, not arrived => misdirection notified once")
  void ci04_misdirectedOnly() {
    Cargo cargo = cargoToStockholm();
    givenHistory(
      event(cargo, RECEIVE, HONGKONG, null, "2009-03-01"),
      event(cargo, LOAD, HONGKONG, v100, "2009-03-03"),
      event(cargo, UNLOAD, TOKYO, v100, "2009-03-05")); 
    service.inspectCargo(ID);

    assertThat(cargo.delivery().isMisdirected()).isTrue();
    assertThat(cargo.delivery().isUnloadedAtDestination()).isFalse();
    verify(applicationEvents, times(1)).cargoWasMisdirected(cargo);
    verify(applicationEvents, never()).cargoHasArrived(any(Cargo.class));
    verify(cargoRepository, times(1)).store(cargo);
  }

  @Test
  @DisplayName("CI-05: not misdirected, arrived => arrival notified once")
  void ci05_arrivedOnly() {
    Cargo cargo = cargoToStockholm();
    givenHistory(event(cargo, UNLOAD, STOCKHOLM, v200, "2009-03-14"));
    service.inspectCargo(ID);

    assertThat(cargo.delivery().isMisdirected()).isFalse();
    assertThat(cargo.delivery().isUnloadedAtDestination()).isTrue();
    verify(applicationEvents, never()).cargoWasMisdirected(any(Cargo.class));
    verify(applicationEvents, times(1)).cargoHasArrived(cargo);
    verify(cargoRepository, times(1)).store(cargo);
  }

  @Test
  @DisplayName("CI-06: misdirected and arrived => both notified once")
  void ci06_misdirectedAndArrived() {
    Cargo cargo = new Cargo(ID, spec(HONGKONG, HAMBURG, DEADLINE), originalItinerary());
    when(cargoRepository.find(ID)).thenReturn(cargo);
    givenHistory(event(cargo, UNLOAD, HAMBURG, v300, "2009-03-12"));

    service.inspectCargo(ID);

    assertThat(cargo.delivery().isMisdirected()).isTrue();
    assertThat(cargo.delivery().isUnloadedAtDestination()).isTrue();
    verify(applicationEvents, times(1)).cargoWasMisdirected(cargo);
    verify(applicationEvents, times(1)).cargoHasArrived(cargo);
    verify(cargoRepository, times(1)).store(cargo);
  }
}
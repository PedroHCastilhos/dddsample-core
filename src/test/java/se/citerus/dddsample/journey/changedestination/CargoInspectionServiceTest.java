package se.citerus.dddsample.journey.changedestination;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import se.citerus.dddsample.application.ApplicationEvents;
import se.citerus.dddsample.application.impl.CargoInspectionServiceImpl;
import se.citerus.dddsample.domain.model.cargo.Cargo;
import se.citerus.dddsample.domain.model.cargo.CargoRepository;
import se.citerus.dddsample.domain.model.cargo.Delivery;
import se.citerus.dddsample.domain.model.cargo.TrackingId;
import se.citerus.dddsample.domain.model.handling.HandlingEventRepository;
import se.citerus.dddsample.domain.model.handling.HandlingHistory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CargoInspectionServiceTest {

  private static final TrackingId ID = new TrackingId("CI1");

  private ApplicationEvents applicationEvents;
  private CargoRepository cargoRepository;
  private HandlingEventRepository handlingEventRepository;
  private CargoInspectionServiceImpl service;
  private Cargo cargo;
  private Delivery delivery;

  @BeforeEach
  void setUp() {
    applicationEvents = mock(ApplicationEvents.class);
    cargoRepository = mock(CargoRepository.class);
    handlingEventRepository = mock(HandlingEventRepository.class);
    service = new CargoInspectionServiceImpl(applicationEvents, cargoRepository, handlingEventRepository);

    cargo = mock(Cargo.class);
    delivery = mock(Delivery.class);
    when(cargo.delivery()).thenReturn(delivery);
    when(cargoRepository.find(ID)).thenReturn(cargo);
    when(handlingEventRepository.lookupHandlingHistoryOfCargo(ID)).thenReturn(HandlingHistory.EMPTY);
  }

  private void givenDelivery(boolean misdirected, boolean unloadedAtDestination) {
    when(delivery.isMisdirected()).thenReturn(misdirected);
    when(delivery.isUnloadedAtDestination()).thenReturn(unloadedAtDestination);
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
    givenDelivery(false, false);

    service.inspectCargo(ID);

    verify(applicationEvents, never()).cargoWasMisdirected(any(Cargo.class));
    verify(applicationEvents, never()).cargoHasArrived(any(Cargo.class));
    verify(cargo).deriveDeliveryProgress(HandlingHistory.EMPTY);
    verify(cargoRepository, times(1)).store(cargo);
  }

  @Test
  @DisplayName("CI-04: misdirected, not arrived => misdirection notified once")
  void ci04_misdirectedOnly() {
    givenDelivery(true, false);

    service.inspectCargo(ID);

    verify(applicationEvents, times(1)).cargoWasMisdirected(cargo);
    verify(applicationEvents, never()).cargoHasArrived(any(Cargo.class));
    verify(cargoRepository, times(1)).store(cargo);
  }

  @Test
  @DisplayName("CI-05: not misdirected, arrived => arrival notified once")
  void ci05_arrivedOnly() {
    givenDelivery(false, true);

    service.inspectCargo(ID);

    verify(applicationEvents, never()).cargoWasMisdirected(any(Cargo.class));
    verify(applicationEvents, times(1)).cargoHasArrived(cargo);
    verify(cargoRepository, times(1)).store(cargo);
  }

  @Test
  @DisplayName("CI-06: misdirected and arrived => both notified once")
  void ci06_misdirectedAndArrived() {
    givenDelivery(true, true);

    service.inspectCargo(ID);

    verify(applicationEvents, times(1)).cargoWasMisdirected(cargo);
    verify(applicationEvents, times(1)).cargoHasArrived(cargo);
    verify(cargoRepository, times(1)).store(cargo);
  }
}

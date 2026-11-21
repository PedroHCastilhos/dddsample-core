package se.citerus.dddsample.journey.changedestination;

import se.citerus.dddsample.domain.model.cargo.Cargo;
import se.citerus.dddsample.infrastructure.messaging.stub.SynchronousApplicationEventsStub;

class RecordingApplicationEvents extends SynchronousApplicationEventsStub {

  int misdirectedNotifications;
  int arrivedNotifications;

  @Override
  public void cargoWasMisdirected(Cargo cargo) {
    misdirectedNotifications++;
    super.cargoWasMisdirected(cargo);
  }

  @Override
  public void cargoHasArrived(Cargo cargo) {
    arrivedNotifications++;
    super.cargoHasArrived(cargo);
  }
}

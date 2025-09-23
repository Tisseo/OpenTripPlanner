package org.opentripplanner.ext.vehicleparking.tisseo;

import com.fasterxml.jackson.databind.JsonNode;
import org.opentripplanner.model.calendar.openinghours.OpeningHoursCalendarService;
import org.opentripplanner.service.vehicleparking.model.VehicleParkingSpaces;

public class CarTisseoUpdater extends TisseoUpdater {

  public CarTisseoUpdater(
    TisseoUpdaterParameters parameters,
    OpeningHoursCalendarService openingHoursCalendarService
  ) {
    super(parameters, openingHoursCalendarService);
  }

  // Ovveride parseCapacity and parseAvailability functions
  @Override
  protected VehicleParkingSpaces parseCapacity(JsonNode jsonNode) {
    return parseVehicleSpaces(
      jsonNode,
      null,
      "total_standard_parking_spots",
      "total_prm_parking_spots"
    );
  }

  @Override
  protected VehicleParkingSpaces parseAvailability(JsonNode jsonNode) {
    return parseVehicleSpaces(
      jsonNode,
      null,
      "realtime_infos.free_standard_parking_spots",
      "realtime_infos.free_prm_parking_spots"
    );
  }
}

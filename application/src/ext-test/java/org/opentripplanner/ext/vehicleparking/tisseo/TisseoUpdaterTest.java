package org.opentripplanner.ext.vehicleparking.tisseo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opentripplanner._support.time.ZoneIds;
import org.opentripplanner.framework.io.HttpHeaders;
import org.opentripplanner.street.model.openinghours.OpeningHoursCalendarService;
import org.opentripplanner.test.support.ResourceLoader;
import org.opentripplanner.transit.model.framework.Deduplicator;

public class TisseoUpdaterTest {

  private static final Duration FREQUENCY = Duration.ofSeconds(30);
  private static final ResourceLoader LOADER = ResourceLoader.of(TisseoUpdaterTest.class);
  private static final String TISSEO_PR_URL = LOADER.uri("tisseo.json").toString();

  @Test
  void parseCars() {
    var timeZone = ZoneIds.PARIS;
    var parameters = new TisseoUpdaterParameters(
      "",
      TISSEO_PR_URL,
      "FR",
      FREQUENCY,
      HttpHeaders.empty(),
      List.of(),
      null,
      timeZone
    );
    var openingHoursCalendarService = new OpeningHoursCalendarService(
      new Deduplicator(),
      LocalDate.of(2022, Month.JANUARY, 1),
      LocalDate.of(2023, Month.JANUARY, 1)
    );
    var updater = new CarTisseoUpdater(parameters, openingHoursCalendarService);

    assertTrue(updater.update());
    var parkingLots = updater.getUpdates();

    assertEquals(23, parkingLots.size());

    var first = parkingLots.get(0);
    assertEquals("Aéroconstellation", first.getName().toString());

    var entrance = first.getEntrances().get(0);
    assertTrue(entrance.isCarAccessible());
    assertTrue(entrance.isWalkAccessible());
  }
}

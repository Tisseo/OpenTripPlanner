package org.opentripplanner.ext.vehicleparking.tisseo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.osm.OsmOpeningHoursParser;
import org.opentripplanner.service.vehicleparking.model.VehicleParking;
import org.opentripplanner.service.vehicleparking.model.VehicleParkingSpaces;
import org.opentripplanner.service.vehicleparking.model.VehicleParkingState;
import org.opentripplanner.street.geometry.WgsCoordinate;
import org.opentripplanner.street.model.openinghours.OpeningHoursCalendarService;
import org.opentripplanner.updater.spi.GenericJsonDataSource;
import org.opentripplanner.utils.tostring.ToStringBuilder;

/**
 * Vehicle parking ParkRide Tisseo.
 */
abstract class TisseoUpdater extends GenericJsonDataSource<VehicleParking> {

  private static final String JSON_PARSE_PATH = "park_and_ride";
  private static final String JSON_PATH_REALTIME = "realtime_infos";

  private final String feedId;
  private final Collection<String> staticTags;
  private final OsmOpeningHoursParser osmOpeningHoursParser;
  private final String url;

  protected TisseoUpdater(
    TisseoUpdaterParameters parameters,
    OpeningHoursCalendarService openingHoursCalendarService
  ) {
    super(parameters.url(), JSON_PARSE_PATH, parameters.httpHeaders());
    this.feedId = parameters.feedId();
    this.staticTags = parameters.tags();
    this.osmOpeningHoursParser = new OsmOpeningHoursParser(
      openingHoursCalendarService,
      parameters.timeZone()
    );
    this.url = parameters.url();
  }

  @Override
  protected VehicleParking parseElement(JsonNode jsonNode) {
    // Retrieving capacity,availability,vehicleParkId,vehicleParName,coordinate(x,y) and state
    var capacity = parseCapacity(jsonNode);
    var availability = parseAvailability(jsonNode);
    var bicyclePlaces = false;

    var vehicleParkId = createIdForNode(jsonNode);
    var vehicleParkName = new NonLocalizedString(jsonNode.path("name").asText());

    double x = jsonNode.path("x").asDouble();
    double y = jsonNode.path("y").asDouble();

    var state = parseState(jsonNode);

    // Create entrance
    VehicleParking.VehicleParkingEntranceCreator entrance = builder ->
      builder
        .entranceId(new FeedScopedId(feedId, vehicleParkId.getId() + "/entrance"))
        .coordinate(new WgsCoordinate(y, x))
        .name(vehicleParkName)
        .walkAccessible(true)
        .carAccessible(true);

    // Retrieving information regarding the total number of carPlaces, freeCarPlaces, freeWheelchairPlaces,state
    int freeCarPlaces = 0;
    int freeWheelchairAccesiblesCarPlaces = 0;
    if (jsonNode.has(JSON_PATH_REALTIME) && !jsonNode.get(JSON_PATH_REALTIME).isEmpty()) {
      freeCarPlaces = jsonNode.path(JSON_PATH_REALTIME).path("free_standard_parking_spots").asInt();
      freeWheelchairAccesiblesCarPlaces = jsonNode
        .path(JSON_PATH_REALTIME)
        .path("free_prm_parking_spots")
        .asInt();
    }

    var wheelChairAccessiblePlaces = freeWheelchairAccesiblesCarPlaces > 0;
    var carPlaces = freeCarPlaces > 0;
    var tags = parseTags(jsonNode, "lot_type", "forecast", "state");
    tags.addAll(staticTags);

    return VehicleParking.builder()
      .id(vehicleParkId)
      .name(vehicleParkName)
      .state(state)
      .coordinate(new WgsCoordinate(y, x))
      .capacity(capacity)
      .availability(availability)
      .bicyclePlaces(bicyclePlaces)
      .carPlaces(carPlaces)
      .entrance(entrance)
      .wheelchairAccessibleCarPlaces(wheelChairAccessiblePlaces)
      .tags(tags)
      .build();
  }

  protected VehicleParkingSpaces parseVehicleSpaces(
    JsonNode node,
    String bicycleTag,
    String carTag,
    String wheelchairAccessibleCarTag
  ) {
    var bicycleSpaces = parseSpacesValue(node, bicycleTag);
    var carSpaces = parseSpacesValue(node, carTag);
    var wheelchairAccessibleCarSpaces = parseSpacesValue(node, wheelchairAccessibleCarTag);

    if (bicycleSpaces == null && carSpaces == null && wheelchairAccessibleCarSpaces == null) {
      return null;
    }

    return createVehiclePlaces(carSpaces, wheelchairAccessibleCarSpaces, bicycleSpaces);
  }

  abstract VehicleParkingSpaces parseCapacity(JsonNode jsonNode);

  abstract VehicleParkingSpaces parseAvailability(JsonNode jsonNode);

  private VehicleParkingSpaces createVehiclePlaces(
    Integer carSpaces,
    Integer wheelchairAccessibleCarSpaces,
    Integer bicycleSpaces
  ) {
    return VehicleParkingSpaces.builder()
      .bicycleSpaces(bicycleSpaces)
      .carSpaces(carSpaces)
      .wheelchairAccessibleCarSpaces(wheelchairAccessibleCarSpaces)
      .build();
  }

  // Retrieves the value of a nested field from a JsonNode.
  private Integer parseSpacesValue(JsonNode jsonNode, String fieldName) {
    if (jsonNode == null || fieldName == null || fieldName.isBlank()) {
      return null;
    }
    // Split the field path by "." to handle nested fields
    String[] fields = fieldName.split("\\.");
    JsonNode currentNode = jsonNode;

    // Browse each level of the path
    for (String field : fields) {
      currentNode = currentNode.get(field);
      if (currentNode == null || currentNode.isMissingNode()) {
        //Field does not exist
        return null;
      }
    }
    // Return the value as Int
    return currentNode.asInt();
  }

  private VehicleParkingState parseState(JsonNode jsonNode) {
    if (jsonNode.has(JSON_PATH_REALTIME) && !jsonNode.get(JSON_PATH_REALTIME).isEmpty()) {
      var stateText = jsonNode.path(JSON_PATH_REALTIME).path("state").asText();

      return stateText.equals("CLOSED") || stateText.equals("CLOSED_FORCED")
        ? VehicleParkingState.CLOSED
        : VehicleParkingState.OPERATIONAL;
    }
    return VehicleParkingState.OPERATIONAL;
  }

  private FeedScopedId createIdForNode(JsonNode jsonNode) {
    String id;
    var newFeedId = feedId;
    if (jsonNode.has("id")) {
      id = jsonNode.path("id").asText();
    } else {
      id = String.format(
        "%s/%f/%f",
        jsonNode.get("name"),
        jsonNode.path("coords").path("lng").asDouble(),
        jsonNode.path("coords").path("lat").asDouble()
      );
    }
    return new FeedScopedId(newFeedId, id);
  }

  private List<String> parseTags(JsonNode node, String... tagNames) {
    var tagList = new ArrayList<String>();
    for (var tagName : tagNames) {
      if (node.has(tagName)) {
        tagList.add(tagName + ":" + node.get(tagName).asText());
      }
    }
    return tagList;
  }

  @Override
  public String toString() {
    return ToStringBuilder.of(getClass()).addStr("feedId", feedId).addObj("url", url).toString();
  }
}

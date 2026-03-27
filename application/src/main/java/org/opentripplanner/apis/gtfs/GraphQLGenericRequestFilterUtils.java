package org.opentripplanner.apis.gtfs;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraphQLGenericRequestFilterUtils {

  /**
   * Apply a list of generic key/value filters to a stream of items.
   *
   * @param items the input Stream of items to filter
   * @param filters list of maps {key,value} representing the filters, each map containing "key" and "value" strings
   * @param extractors map of prefix -> extractor function that returns related objects for an item
   * @param <T> the item type
   * @return a Stream containing only items that match all provided filters
   */
  public static <T> Stream<T> applyFilters(
    Stream<T> items,
    List<Map<String, String>> filters,
    Map<String, Function<T, Set<Object>>> extractors
  ) {
    // If there are no filters, return the original stream
    if (filters == null || filters.isEmpty()) {
      return items;
    }

    for (Map<String, String> filter : filters) {
      String key = filter.get("key");
      if (key == null || key.isBlank()) {
        continue;
      }

      // Read the filter value (comma-separated) and convert to a Set<String> of accepted values.
      Set<String> acceptedValues = Arrays.stream(filter.get("value").split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toSet());

      if (acceptedValues.isEmpty()) {
        continue;
      }

      // Find extractor that matches the key prefix
      Function<T, Set<Object>> extractor = null;
      String subKey = null;
      for (String exKey : extractors.keySet()) {
        if (key.equals(exKey) || key.startsWith(exKey)) {
          extractor = extractors.get(exKey);
          // Determine the remainder of the key
          // Example: key="route.shortName", exKey="route" -> subKey=".shortName"
          subKey = key.length() > exKey.length() ? key.substring(exKey.length()) : null;

          if (subKey != null && subKey.startsWith(".")) {
            subKey = subKey.substring(1);
          }
          // Stop searching once we found a matching extractor.
          break;
        }
      }

      // Default extractor
      if (extractor == null) {
        // If no extractor matches the key (like "route", "station", etc.),
        // we don’t need to jump to a related object --> we’ll just inspect the item itself.
        extractor = item -> Set.of(item);
        subKey = key;
      }

      final Function<T, Set<Object>> finalExtractor = extractor;
      final String finalSubKey = subKey;

      // Replace 'items' with a filtered stream
      items = items.filter(item -> {
        // Run the extractor on the item to get base objects:
        //   - Could be the item itself (if no extractor matched, like "shortName, type").
        //   - Could be related objects (if an extractor was used, like "route").
        Set<Object> baseObjects = finalExtractor.apply(item);
        if (baseObjects == null || baseObjects.isEmpty()) {
          return false;
        }

        // For each base object, get the string values at the given path (via extractValues)
        // Then check if ANY of those values are in acceptedValues.
        boolean matched = baseObjects
          .stream()
          .filter(Objects::nonNull)
          .flatMap(obj -> extractValues(obj, finalSubKey).stream())
          .anyMatch(acceptedValues::contains);
        // Keep the item only if there was at least one match.
        return matched;
      });
    }
    // return the resulting stream.
    return items;
  }

  /**
   * Extract string values from an object following a dot-separated path.
   * Example:
   * - "id" => getId()
   * - "parentStation.id" => getParentStation().getId()
   *
   * @param obj  root object to extract values from
   * @param path dot-separated path of promerty names (e.g., "route.shortName")
   * @return set of string values extracted from the given path of the object.
   */
  private static Set<String> extractValues(Object obj, String path) {
    if (obj == null) {
      return Collections.emptySet();
    }
    if (path == null || path.isEmpty()) {
      return Set.of(obj.toString());
    }

    Object current = obj;
    try {
      // Split the path by '.' to navigate nested fields
      for (String part : path.split("\\.")) {
        // nothing can be extracted
        if (current == null) {
          return Collections.emptySet();
        }
        // Build the getter method name. "id" -> "getId", "shortName" -> "getShortName"
        String methodName = "get" + Character.toUpperCase(part.charAt(0)) + part.substring(1);
        // find the getter method on the current class
        Method method = current.getClass().getMethod(methodName);
        current = method.invoke(current);
      }
    } catch (Exception e) {
      // If any error (method not found, invocation failure, etc.),
      // return an empty set to indicate "no values"
      return Collections.emptySet();
    }

    if (current == null) {
      return Collections.emptySet();
    }

    // Convert all elements to strings
    if (current instanceof Collection<?>) {
      return ((Collection<?>) current).stream()
        .filter(Objects::nonNull)
        .map(Object::toString)
        .collect(Collectors.toSet());
    } else if (current.getClass().isArray()) {
      return Arrays.stream((Object[]) current)
        .filter(Objects::nonNull)
        .map(Object::toString)
        .collect(Collectors.toSet());
    } else {
      return Set.of(current.toString());
    }
  }
}

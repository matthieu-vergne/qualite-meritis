package fr.vergne.qualitemeritis;

import static java.util.Collections.emptyList;
import static java.util.Collections.shuffle;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class OfferBestSeatsInPriceRangeTest {

	static Stream<Integer> seatsCount() {
		return Stream.of(1, 2, 5, 10, 100);
	}

	@ParameterizedTest(name = "{0} seats")
	@MethodSource("seatsCount")
	void testReturnsAllSeatsAssumingExactlyMatchingPriceRange(int seatsCount) {
		// GIVEN
		Price price = Price.euros(5);
		List<Seat> allSeats = range(0, seatsCount).mapToObj(i -> new Seat(price)).toList();
		SuggestionSystem system = new SuggestionSystem(allSeats, allSeatsFree(), noSeatDistance(),
				noMiddleRowDistance(), noStageDistance());
		PriceRange priceRange = new PriceRange(price, price);

		// WHEN
		Collection<Seat> result = system.offerBestSeatsIn(priceRange, noParty());

		// THEN
		assertEquals(allSeats, result);
	}

	static Stream<Object[]> seatIndexAndCount() {
		// The index is one-based to offer a better report readability
		return seatsCount().flatMap(count -> {
			return Stream.of(//
					Arrays.asList(1, count), // first seat
					Arrays.asList((count + 1) / 2, count), // middle seat
					Arrays.asList(count, count)// last seat
			);
		})//
				.distinct()// Remove redundant cases
				.map(List::toArray); // As Object[] to be compatible with parameterized tests
	}

	@ParameterizedTest(name = "seat {0} in {1}")
	@MethodSource("seatIndexAndCount")
	void testReturnsOnlySeatMatchingPriceRange(int seatIndex, int seatsCount) {
		// GIVEN
		Supplier<Price> priceSupplier = createIncrementingPricesPer(1);
		List<Seat> allSeats = range(0, seatsCount).mapToObj(i -> new Seat(priceSupplier.get())).toList();
		SuggestionSystem system = new SuggestionSystem(allSeats, allSeatsFree(), noSeatDistance(),
				noMiddleRowDistance(), noStageDistance());
		Seat targetSeat = allSeats.get(seatIndex - 1);
		Price targetPrice = targetSeat.price();
		PriceRange priceRange = new PriceRange(targetPrice, targetPrice);

		// WHEN
		Collection<Seat> result = system.offerBestSeatsIn(priceRange, noParty());

		// THEN
		assertEquals(Arrays.asList(targetSeat), result);
	}

	@ParameterizedTest(name = "seat {0} in {1}")
	@MethodSource("seatIndexAndCount")
	void testReturnsOnlySeatWithinPriceRange(int seatIndex, int seatsCount) {
		// GIVEN
		Supplier<Price> priceSupplier = createIncrementingPricesPer(10);
		List<Seat> allSeats = range(0, seatsCount).mapToObj(i -> new Seat(priceSupplier.get())).toList();
		SuggestionSystem system = new SuggestionSystem(allSeats, allSeatsFree(), noSeatDistance(),
				noMiddleRowDistance(), noStageDistance());
		Seat targetSeat = allSeats.get(seatIndex - 1);
		Price targetPrice = targetSeat.price();
		PriceRange priceRange = new PriceRange(targetPrice.minus(1), targetPrice.plus(1));

		// WHEN
		Collection<Seat> result = system.offerBestSeatsIn(priceRange, noParty());

		// THEN
		assertEquals(Arrays.asList(targetSeat), result);
	}

	@ParameterizedTest(name = "seat {0} in {1}")
	@MethodSource("seatIndexAndCount")
	void testReturnsAllButSingleBookedSeat(int seatIndex, int seatsCount) {
		// GIVEN
		Price price = Price.euros(10);
		List<Seat> allSeats = range(0, seatsCount).mapToObj(i -> new Seat(price)).toList();
		List<Seat> freeSeats = new ArrayList<>(allSeats);
		Seat bookedSeat = freeSeats.remove(seatIndex - 1);
		Predicate<Seat> freeSeatPredicate = seat -> !bookedSeat.equals(seat);
		SuggestionSystem system = new SuggestionSystem(allSeats, freeSeatPredicate, noSeatDistance(),
				noMiddleRowDistance(), noStageDistance());
		PriceRange priceRange = new PriceRange(price, price);

		// WHEN
		Collection<Seat> result = system.offerBestSeatsIn(priceRange, noParty());

		// THEN
		assertEquals(freeSeats, result);
	}

	@ParameterizedTest(name = "{0} seats")
	@MethodSource("seatsCount")
	void testReturnsNoSeatAssumingExactlyMatchingPriceRangeButAllBooked(int seatsCount) {
		// GIVEN
		Price price = Price.euros(5);
		List<Seat> allSeats = range(0, seatsCount).mapToObj(i -> new Seat(price)).toList();
		Predicate<Seat> freeSeatPredicate = seat -> false;
		SuggestionSystem system = new SuggestionSystem(allSeats, freeSeatPredicate, noSeatDistance(),
				noMiddleRowDistance(), noStageDistance());
		PriceRange priceRange = new PriceRange(price, price);

		// WHEN
		Collection<Seat> result = system.offerBestSeatsIn(priceRange, noParty());

		// THEN
		assertEquals(emptyList(), result);
	}

	static Stream<Integer> seatsCountForAdjacency() {
		return Stream.of(3, 5, 10, 100);
	}

	@ParameterizedTest(name = "{0} seats")
	@MethodSource("seatsCountForAdjacency")
	void testReturnsSeatsAdjacentToSingleParty(int seatsCount) {
		// GIVEN
		Price price = Price.euros(5);
		List<Seat> allSeats = range(0, seatsCount).mapToObj(i -> new Seat(price)).toList();

		List<Seat> adjacentSeats = new ArrayList<>(allSeats);
		shuffle(adjacentSeats, new Random(0));
		Seat partySeat = adjacentSeats.remove(0);
		adjacentSeats = adjacentSeats.subList(0, adjacentSeats.size() / 2);

		List<Seat> party = Arrays.asList(partySeat);
		Predicate<Seat> freeSeatPredicate = seat -> !party.contains(seat);
		BiFunction<Seat, Seat, Integer> seatsDistancer = adjacencyDistance(partySeat, adjacentSeats);
		SuggestionSystem system = new SuggestionSystem(allSeats, freeSeatPredicate, seatsDistancer,
				noMiddleRowDistance(), noStageDistance());
		PriceRange priceRange = new PriceRange(price, price);

		// WHEN
		List<Seat> result = system.offerBestSeatsIn(priceRange, party);

		// THEN
		assertEqualsUnordered(adjacentSeats, result.subList(0, adjacentSeats.size()));
	}

	@Test
	void testReturnsSeatsAdjacentToGroupParty() {
		// GIVEN
		Price price = Price.euros(5);
		// Create a lot of seats
		List<Seat> allSeats = range(0, 100).mapToObj(i -> new Seat(price)).collect(toList());

		// Extract a few seats for party and adjacent seats
		// The suggestion should prioritize these few in the mass
		Iterator<Seat> remainingSeat = allSeats.iterator();
		Map<Seat, Collection<Seat>> adjacencies = range(0, 3)//
				.mapToObj(i -> i)// Transform to object stream to access collect method
				.collect(toMap(//
						// party seat as key
						i -> remainingSeat.next(), //
						// list of few adjacent seats as value
						i -> Arrays.asList(remainingSeat.next(), remainingSeat.next())//
				));
		BiFunction<Seat, Seat, Integer> seatsDistancer = adjacencyDistance(adjacencies);
		shuffle(allSeats, new Random(0));// Ensure uncorrelated order with adjacencies

		Collection<Seat> party = adjacencies.keySet();
		Collection<Seat> adjacentSeats = mergedValues(adjacencies);
		Predicate<Seat> freeSeatPredicate = seat -> !party.contains(seat);
		SuggestionSystem system = new SuggestionSystem(allSeats, freeSeatPredicate, seatsDistancer,
				noMiddleRowDistance(), noStageDistance());
		PriceRange priceRange = new PriceRange(price, price);

		// WHEN
		List<Seat> result = system.offerBestSeatsIn(priceRange, party);

		// THEN
		assertEqualsUnordered(adjacentSeats, result.subList(0, adjacentSeats.size()));
	}

	@Test
	void testReturnsSeatsNearestToMiddleOfRow() {
		// GIVEN
		Price price = Price.euros(5);
		// Create a lot of seats
		List<Seat> allSeats = range(0, 100).mapToObj(i -> new Seat(price)).collect(toList());

		// Fix the seats distance to the middle based on their index.
		// It totally constrains the expected result.
		List<Seat> expected = new ArrayList<>(allSeats);
		Function<Seat, Integer> middleRowDistancer = seat -> expected.indexOf(seat);
		shuffle(allSeats, new Random(0));// Ensure uncorrelated order with expected result

		SuggestionSystem system = new SuggestionSystem(allSeats, allSeatsFree(), noSeatDistance(), middleRowDistancer,
				noStageDistance());
		PriceRange priceRange = new PriceRange(price, price);

		// WHEN
		List<Seat> result = system.offerBestSeatsIn(priceRange, noParty());

		// THEN
		assertEquals(expected, result);
	}

	@Test
	void testReturnsSeatsNearestToStage() {
		// GIVEN
		Price price = Price.euros(5);
		// Create a lot of seats
		List<Seat> allSeats = range(0, 100).mapToObj(i -> new Seat(price)).collect(toList());

		// Fix the seats distance to the middle based on their index.
		// It totally constrains the expected result.
		List<Seat> expected = new ArrayList<>(allSeats);
		Function<Seat, Integer> stageDistancer = seat -> expected.indexOf(seat);
		shuffle(allSeats, new Random(0));// Ensure uncorrelated order with expected result

		SuggestionSystem system = new SuggestionSystem(allSeats, allSeatsFree(), noSeatDistance(),
				noMiddleRowDistance(), stageDistancer);
		PriceRange priceRange = new PriceRange(price, price);

		// WHEN
		List<Seat> result = system.offerBestSeatsIn(priceRange, noParty());

		// THEN
		assertEquals(expected, result);
	}

	@Test
	void testReturnsSeatCloseToPartyOverSeatCloseToMiddleOfRow() {
		// GIVEN
		Price price = Price.euros(5);
		Seat partySeat = new Seat(price);
		Seat seatCloseToParty = new Seat(price);
		Seat seatCloseToMiddle = new Seat(price);
		List<Seat> allSeats = Arrays.asList(partySeat, seatCloseToParty, seatCloseToMiddle);

		List<Seat> party = Arrays.asList(partySeat);
		Predicate<Seat> freeSeatPredicate = seat -> !party.contains(seat);
		BiFunction<Seat, Seat, Integer> seatsDistancer = adjacencyDistance(partySeat, Arrays.asList(seatCloseToParty));

		Function<Seat, Integer> middleRowDistancer = seat -> seat.equals(seatCloseToMiddle) ? 0 : 1;

		SuggestionSystem system = new SuggestionSystem(allSeats, freeSeatPredicate, seatsDistancer, middleRowDistancer,
				noStageDistance());
		PriceRange priceRange = new PriceRange(price, price);

		// WHEN
		List<Seat> result = system.offerBestSeatsIn(priceRange, party);

		// THEN
		assertEquals(Arrays.asList(seatCloseToParty, seatCloseToMiddle), result);
	}

	@Test
	void testReturnsSeatCloseToMiddleOfRowOverSeatCloseToStage() {
		// GIVEN
		Price price = Price.euros(5);
		Seat seatCloseToStage = new Seat(price);
		Seat seatCloseToMiddle = new Seat(price);
		List<Seat> allSeats = Arrays.asList(seatCloseToStage, seatCloseToMiddle);

		Function<Seat, Integer> middleRowDistancer = seat -> seat.equals(seatCloseToMiddle) ? 0 : 1;

		Function<Seat, Integer> stageDistancer = seat -> seat.equals(seatCloseToStage) ? 0 : 1;

		SuggestionSystem system = new SuggestionSystem(allSeats, allSeatsFree(), noSeatDistance(), middleRowDistancer,
				stageDistancer);
		PriceRange priceRange = new PriceRange(price, price);

		// WHEN
		List<Seat> result = system.offerBestSeatsIn(priceRange, noParty());

		// THEN
		assertEquals(Arrays.asList(seatCloseToMiddle, seatCloseToStage), result);
	}

	private static Collection<Seat> mergedValues(Map<Seat, Collection<Seat>> adjacencies) {
		return adjacencies.values().stream().flatMap(Collection::stream).toList();
	}

	private static Supplier<Price> createIncrementingPricesPer(int increment) {
		int[] nextValue = { 0 };
		return () -> Price.euros(nextValue[0] += increment);
	}

	private static Predicate<Seat> allSeatsFree() {
		return seat -> true;
	}

	private static BiFunction<Seat, Seat, Integer> noSeatDistance() {
		return (s1, s2) -> 0;
	}

	private static Function<Seat, Integer> noMiddleRowDistance() {
		return seat -> 0;
	}

	private static Function<Seat, Integer> noStageDistance() {
		return seat -> 0;
	}

	private static List<Seat> noParty() {
		return emptyList();
	}

	private static BiFunction<Seat, Seat, Integer> adjacencyDistance(Seat partySeat, Collection<Seat> adjacentSeats) {
		return (seat1, seat2) -> {
			if (seat1.equals(seat2)) {
				return 0;
			} else if ((partySeat.equals(seat1) && adjacentSeats.contains(seat2))//
					|| (partySeat.equals(seat2) && adjacentSeats.contains(seat1))) {
				return 1;
			} else {
				return 2;
			}
		};
	}

	private static BiFunction<Seat, Seat, Integer> adjacencyDistance(Map<Seat, Collection<Seat>> adjacencies) {
		List<BiFunction<Seat, Seat, Integer>> distancers = adjacencies.entrySet().stream()//
				.map(entry -> adjacencyDistance(entry.getKey(), entry.getValue()))//
				.toList();
		return (seat1, seat2) -> {
			return distancers.stream()//
					.mapToInt(distancer -> distancer.apply(seat1, seat2))//
					.min().getAsInt();
		};
	}

	private static void assertEqualsUnordered(Collection<Seat> expected, Collection<Seat> actual) {
		assertEquals(new HashSet<>(expected), new HashSet<>(actual));
	}

}

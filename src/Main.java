import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
//main
public class Main {
    private static final Scanner scanner = new Scanner(System.in);
    private static final MatchingEngine matchingEngine = new MatchingEngine();
    private static final PlatformAdmin platformAdmin = new PlatformAdmin();
    private static final PaymentGateway paymentGateway = new PaymentGateway();
    private static final List<Customer> customers = new ArrayList<>();
    private static final List<Driver> drivers = new ArrayList<>();
    private static final List<Trip> trips = new ArrayList<>();
    private static int nextTripId = 1;

    public static void main(String[] args) {
        initializeSampleData();
        printHeader();

        while (true) {
            System.out.println("1. Request a ride");
            System.out.println("2. View trip history");
            System.out.println("3. View drivers status");
            System.out.println("4. Exit");
            System.out.print("Choose an option: ");
            String option = scanner.nextLine().trim();

            switch (option) {
                case "1":
                    handleRideRequest();
                    break;
                case "2":
                    showTripHistory();
                    break;
                case "3":
                    showDriversStatus();
                    break;
                case "4":
                    System.out.println("Exiting platform. Goodbye.");
                    return;
                default:
                    System.out.println("Invalid option. Please choose again.");
            }
        }
    }

    private static void printHeader() {
        System.out.println("Ride-Sharing / Cab Booking Platform Simulation");
        System.out.println("Preconditions: customer account active, saved payment method, drivers registered, verified, online, GPS enabled, pricing configured.");
    }

    private static void initializeSampleData() {
        Driver driver1 = new Driver("D001", "Rahul", "9999000011", "Toyota Innova", "Koramangala", 4.9, true, 1200.0, true);
        Driver driver2 = new Driver("D002", "Sneha", "9999000022", "Hyundai Verna", "MG Road", 4.8, true, 900.0, true);
        Driver driver3 = new Driver("D003", "Karan", "9999000033", "Mahindra XUV", "Jayanagar", 4.7, true, 1500.0, true);
        drivers.add(driver1);
        drivers.add(driver2);
        drivers.add(driver3);

        Customer customer1 = new Customer("C001", "Ananya", "8888000011", "VISA **** 1234", 4.8, true);
        Customer customer2 = new Customer("C002", "Arjun", "8888000022", "Mastercard **** 5678", 4.7, true);
        customers.add(customer1);
        customers.add(customer2);
    }

    private static void handleRideRequest() {
        Customer customer = chooseCustomer();
        if (customer == null) {
            return;
        }
        if (!customer.isActive()) {
            System.out.println("Customer account is not active. Cannot request a ride.");
            return;
        }

        System.out.print("Enter pickup location: ");
        String pickupLocation = scanner.nextLine().trim();
        System.out.print("Enter drop location: ");
        String dropLocation = scanner.nextLine().trim();

        if (pickupLocation.isEmpty() || dropLocation.isEmpty()) {
            System.out.println("Pickup and drop locations are required.");
            return;
        }

        double routeDistanceKm = matchingEngine.estimateRouteDistance(pickupLocation, dropLocation);
        int estimatedEta = matchingEngine.estimateEta(routeDistanceKm);

        System.out.printf("Estimated route distance: %.1f km\n", routeDistanceKm);
        System.out.printf("Estimated ETA: %d minutes\n", estimatedEta);

        RideType rideType = chooseRideType();
        double surgeMultiplier = platformAdmin.getSurgeMultiplier();
        double estimatedFare = FareCalculationModel.calculateEstimatedFare(rideType, routeDistanceKm, estimatedEta, surgeMultiplier);

        System.out.printf("Selected ride type: %s\n", rideType);
        System.out.printf("Surge multiplier: %.1fx\n", surgeMultiplier);
        System.out.printf("Estimated fare: Rs %.2f\n", estimatedFare);

        if (!customer.hasSavedPaymentMethod()) {
            System.out.println("No saved payment method found. Please update payment method before requesting a ride.");
            return;
        }

        Trip trip = new Trip("T" + formatTripId(), customer.getCustomerId(), null, pickupLocation, dropLocation, rideType, surgeMultiplier, routeDistanceKm);
        trip.setEstimatedFare(estimatedFare);
        trip.setEstimatedEtaMinutes(estimatedEta);

        Driver matchedDriver = matchingEngine.findNearestDriver(drivers, pickupLocation, routeDistanceKm);
        if (matchedDriver == null) {
            System.out.println("No driver available nearby. Expanding search radius...");
            matchedDriver = matchingEngine.expandSearchAndFindDriver(drivers, pickupLocation, routeDistanceKm);
            if (matchedDriver == null) {
                System.out.println("No drivers found after expanding search radius. Please try again later.");
                return;
            }
            System.out.println("Matched driver in expanded radius. Please expect a longer wait.");
        }

        trip.setDriverId(matchedDriver.getDriverId());
        System.out.printf("Driver %s (%s, %s) is available and notified.\n", matchedDriver.getName(), matchedDriver.getVehicleDetails(), matchedDriver.getPhone());

        boolean driverAccepted = matchingEngine.notifyDriverAndAccept(matchedDriver, trip);
        if (!driverAccepted) {
            System.out.println("Driver cancelled after acceptance. Rematching with another drivr");
            Driver rematchedDriver = matchingEngine.rematchDriver(drivers, matchedDriver, pickupLocation, routeDistanceKm);
            if (rematchedDriver == null) {
                System.out.println("Unable to rematch a driver. Please try again later.");
                return;
            }
            trip.setDriverId(rematchedDriver.getDriverId());
            System.out.printf("New driver %s assigned. You will not be charged for this retry.\n", rematchedDriver.getName());
            matchedDriver = rematchedDriver;
        }

        trip.setStatus(TripStatus.ASSIGNED);
        trips.add(trip);
        customer.getTripHistory().add(trip);

        System.out.println("Customer receives driver details and live tracking updates.");
        System.out.printf("Driver %s is on the way. ETA %d minutes.\n", matchedDriver.getName(), estimatedEta);

        System.out.println("Press enter when driver has picked up customer.");
        scanner.nextLine();

        if (!matchedDriver.isGpsEnabled()) {
            System.out.println("GPS not available. Please enable location services and try again.");
            return;
        }

        trip.start();
        System.out.println("Trip has started.");

        if (matchingEngine.didDriverNoShow()) {
            System.out.println("Driver no show for more than 5 minutes. Auto cancelling trip and customer is not charged.");
            trip.setStatus(TripStatus.CANCELLED);
            trip.setCancellationFee(0.0);
            return;
        }

        System.out.println("Press enter when trip reaches destination.");
        scanner.nextLine();

        trip.complete();
        double actualFare = FareCalculationModel.calculateActualFare(rideType, trip.getRouteDistanceKm(), trip.getDurationMinutes(), surgeMultiplier);
        trip.setActualFare(actualFare);
        trip.setStatus(TripStatus.COMPLETED);

        System.out.printf("Trip completed. Total fare: Rs %.2f\n", actualFare);
        boolean paymentSuccess = paymentGateway.processPayment(customer, actualFare);
        if (!paymentSuccess) {
            System.out.println("Payment failed. Please update your payment method.");
            customer.setHasValidPayment(false);
            System.out.print("Enter new saved payment method to retry: ");
            String newPayment = scanner.nextLine().trim();
            customer.setSavedPaymentMethod(newPayment);
            customer.setHasValidPayment(true);
            paymentSuccess = paymentGateway.processPayment(customer, actualFare);
        }

        if (paymentSuccess) {
            System.out.println("Payment successful. Driver earnings credited.");
            matchedDriver.creditEarnings(actualFare * 0.8);
            platformAdmin.recordTrip(trip);
        } else {
            System.out.println("Payment still failed. Trip saved as pending payment.");
        }

        System.out.print("Please rate your driver (1-5): ");
        int customerScore = readIntegerInRange(1, 5);
        System.out.print("Write a quick comment: ");
        String customerComment = scanner.nextLine();
        Rating customerRating = new Rating("R" + trip.getTripId(), trip.getTripId(), RaterType.CUSTOMER, customerScore, customerComment);
        platformAdmin.recordRating(customerRating);
        matchedDriver.updateRating(customerScore);

        System.out.print("Ask driver to rate you? (yes/no): ");
        String askDriverRating = scanner.nextLine().trim().toLowerCase();
        if (askDriverRating.startsWith("y")) {
            int driverScore = 5;
            System.out.print("Driver rating for customer (1-5): ");
            driverScore = readIntegerInRange(1, 5);
            System.out.print("Comment from driver: ");
            String driverComment = scanner.nextLine();
            Rating driverRating = new Rating("R" + trip.getTripId() + "D", trip.getTripId(), RaterType.DRIVER, driverScore, driverComment);
            platformAdmin.recordRating(driverRating);
            customer.updateRating(driverScore);
        }

        System.out.println("Trip record saved with route, duration, fare, payment, and rating updates.");
    }
    //choosing customer
    private static Customer chooseCustomer() {
        System.out.println("Choose a customer:");
        for (int i = 0; i < customers.size(); i++) {
            Customer c = customers.get(i);
            System.out.printf("%d. %s (%s)\n", i + 1, c.getName(), c.getPhone());
        }
        System.out.print("Enter choice: ");
        int choice = readIntegerInRange(1, customers.size());
        return customers.get(choice - 1);
    }

    private static RideType chooseRideType() {
        System.out.println("Choose ride type:");
        System.out.println("1. ECONOMY");
        System.out.println("2. PREMIUM");
        System.out.println("3. XL");
        System.out.print("Enter choice: ");
        int choice = readIntegerInRange(1, 3);
        switch (choice) {
            case 2:
                return RideType.PREMIUM;
            case 3:
                return RideType.XL;
            default:
                return RideType.ECONOMY;
        }
    }

    private static int readIntegerInRange(int min, int max) {
        while (true) {
            try {
                String input = scanner.nextLine().trim();
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
                System.out.printf("Please enter a number between %d and %d: ", min, max);
            } catch (NumberFormatException ex) {
                System.out.print("Invalid number. Please try again: ");
            }
        }
    }

    private static String formatTripId() {
        return String.format("%04d", nextTripId++);
    }
    //show trip history
    private static void showTripHistory() {
        if (trips.isEmpty()) {
            System.out.println("No trip history available.");
            return;
        }
        for (Trip trip : trips) {
            System.out.printf("Trip %s: customer %s, driver %s, from %s to %s, status %s, fare Rs %.2f\n",
                    trip.getTripId(), trip.getCustomerId(), trip.getDriverId(), trip.getPickupLocation(), trip.getDropLocation(), trip.getStatus(), trip.getActualFare());
        }
    }

    private static void showDriversStatus() {
        for (Driver driver : drivers) {
            System.out.printf("Driver %s (%s) - Online: %s, GPS: %s, Rating: %.1f, Earnings Today: Rs %.2f\n",
                    driver.getName(), driver.getVehicleDetails(), driver.isOnline(), driver.isGpsEnabled(), driver.getRating(), driver.getEarningsToday());
        }
    }
}
//driver class
class Driver {
    private final String driverId;
    private final String name;
    private final String phone;
    private final String vehicleDetails;
    private double rating;
    private String location;
    private boolean isOnline;
    private double earningsToday;
    private boolean isVerified;
    private boolean isGpsEnabled;

    public Driver(String driverId, String name, String phone, String vehicleDetails, String location, double rating, boolean isVerified, double earningsToday, boolean isGpsEnabled) {
        this.driverId = driverId;
        this.name = name;
        this.phone = phone;
        this.vehicleDetails = vehicleDetails;
        this.location = location;
        this.rating = rating;
        this.isOnline = true;
        this.earningsToday = earningsToday;
        this.isVerified = isVerified;
        this.isGpsEnabled = isGpsEnabled;
    }

    public String getDriverId() {
        return driverId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getVehicleDetails() {
        return vehicleDetails;
    }

    public double getRating() {
        return rating;
    }

    public String getLocation() {
        return location;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public double getEarningsToday() {
        return earningsToday;
    }

    public boolean isGpsEnabled() {
        return isGpsEnabled;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public void goOnline() {
        this.isOnline = true;
    }

    public void goOffline() {
        this.isOnline = false;
    }

    public void creditEarnings(double amount) {
        this.earningsToday += amount;
    }

    public void updateRating(int score) {
        this.rating = (this.rating + score) / 2.0;
    }
}
// customer class
class Customer {
    private final String customerId;
    private final String name;
    private final String phone;
    private String savedPaymentMethod;
    private double rating;
    private final List<Trip> tripHistory;
    private boolean active;
    private boolean hasValidPayment;

    public Customer(String customerId, String name, String phone, String savedPaymentMethod, double rating, boolean active) {
        this.customerId = customerId;
        this.name = name;
        this.phone = phone;
        this.savedPaymentMethod = savedPaymentMethod;
        this.rating = rating;
        this.tripHistory = new ArrayList<>();
        this.active = active;
        this.hasValidPayment = true;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public boolean hasSavedPaymentMethod() {
        return savedPaymentMethod != null && !savedPaymentMethod.isEmpty() && hasValidPayment;
    }

    public void setSavedPaymentMethod(String savedPaymentMethod) {
        this.savedPaymentMethod = savedPaymentMethod;
    }

    public List<Trip> getTripHistory() {
        return tripHistory;
    }

    public boolean isActive() {
        return active;
    }

    public void cancelRide(Trip trip) {
        trip.setStatus(TripStatus.CANCELLED);
        if (trip.getStatus() == TripStatus.ASSIGNED) {
            trip.setCancellationFee(FareCalculationModel.calculateCancellationFee(true));
        }
    }

    public void requestRide(String pickupLocation, String dropLocation) {
        // Terminal interaction handled in Main.
    }

    public void setHasValidPayment(boolean hasValidPayment) {
        this.hasValidPayment = hasValidPayment;
    }

    public void updateRating(int score) {
        this.rating = (this.rating + score) / 2.0;
    }
}
// trip class
class Trip {
    private final String tripId;
    private final String customerId;
    private String driverId;
    private final String pickupLocation;
    private final String dropLocation;
    private RideType rideType;
    private TripStatus status;
    private double estimatedFare;
    private double actualFare;
    private int estimatedEtaMinutes;
    private int durationMinutes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private double routeDistanceKm;
    private double surgeMultiplier;
    private double cancellationFee;

    public Trip(String tripId, String customerId, String driverId, String pickupLocation, String dropLocation, RideType rideType, double surgeMultiplier, double routeDistanceKm) {
        this.tripId = tripId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.pickupLocation = pickupLocation;
        this.dropLocation = dropLocation;
        this.rideType = rideType;
        this.status = TripStatus.REQUESTED;
        this.surgeMultiplier = surgeMultiplier;
        this.routeDistanceKm = routeDistanceKm;
    }

    public String getTripId() {
        return tripId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getDriverId() {
        return driverId;
    }

    public void setDriverId(String driverId) {
        this.driverId = driverId;
    }

    public String getPickupLocation() {
        return pickupLocation;
    }

    public String getDropLocation() {
        return dropLocation;
    }

    public void setEstimatedFare(double estimatedFare) {
        this.estimatedFare = estimatedFare;
    }

    public void setActualFare(double actualFare) {
        this.actualFare = actualFare;
    }

    public double getActualFare() {
        return actualFare;
    }

    public void setEstimatedEtaMinutes(int estimatedEtaMinutes) {
        this.estimatedEtaMinutes = estimatedEtaMinutes;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public double getRouteDistanceKm() {
        return routeDistanceKm;
    }

    public RideType getRideType() {
        return rideType;
    }

    public void setStatus(TripStatus status) {
        this.status = status;
    }

    public TripStatus getStatus() {
        return status;
    }

    public void setCancellationFee(double cancellationFee) {
        this.cancellationFee = cancellationFee;
    }

    public void start() {
        this.status = TripStatus.IN_PROGRESS;
        this.startTime = LocalDateTime.now();
    }

    public void complete() {
        this.status = TripStatus.COMPLETED;
        this.endTime = LocalDateTime.now();
        this.durationMinutes = (int) Duration.between(startTime, endTime).toMinutes();
        if (durationMinutes <= 0) {
            durationMinutes = 10;
        }
    }
}

class MatchingEngine {
    private double searchRadiusKm = 3.0;
    private int maxWaitSeconds = 300;
    private final Random random = new Random();

    public Driver findNearestDriver(List<Driver> drivers, String pickupLocation, double routeDistanceKm) {
        return drivers.stream()
                .filter(driver -> driver.isOnline() && driver.isVerified() && driver.isGpsEnabled())
                .findFirst()
                .orElse(null);
    }

    public Driver expandSearchAndFindDriver(List<Driver> drivers, String pickupLocation, double routeDistanceKm) {
        double expandedRadius = searchRadiusKm * 2;
        System.out.printf("Expanded search radius to %.1f km.\n", expandedRadius);
        return drivers.stream()
                .filter(driver -> driver.isOnline() && driver.isVerified() && driver.isGpsEnabled())
                .findFirst()
                .orElse(null);
    }

    public boolean notifyDriverAndAccept(Driver driver, Trip trip) {
        if (driver == null) {
            return false;
        }
        boolean willAccept = random.nextDouble() > 0.1;
        if (!willAccept) {
            System.out.printf("Driver %s declined after acceptance.\n", driver.getName());
        }
        return willAccept;
    }
    //rematch driver
    public Driver rematchDriver(List<Driver> drivers, Driver cancelledDriver, String pickupLocation, double routeDistanceKm) {
        return drivers.stream()
                .filter(driver -> !driver.getDriverId().equals(cancelledDriver.getDriverId()))
                .filter(driver -> driver.isOnline() && driver.isVerified() && driver.isGpsEnabled())
                .findFirst()
                .orElse(null);
    }

    public boolean didDriverNoShow() {
        return random.nextDouble() < 0.05;
    }

    public double estimateRouteDistance(String pickupLocation, String dropLocation) {
        return 2 + random.nextDouble() * 8;
    }

    public int estimateEta(double routeDistanceKm) {
        return 5 + (int) Math.round(routeDistanceKm * 2);
    }
}

class FareCalculationModel {
    public static final double BASE_FARE = 30.0;
    public static final double MINIMUM_FARE = 60.0;
    public static final double ECONOMY_PER_KM = 12.0;
    public static final double PREMIUM_PER_KM = 18.0;
    public static final double XL_PER_KM = 22.0;
    public static final double TIME_CHARGE_PER_MIN = 2.0;

    public static double calculateEstimatedFare(RideType rideType, double distanceKm, int etaMinutes, double surgeMultiplier) {
        double distanceCharge = getDistanceRate(rideType) * distanceKm;
        double timeCharge = TIME_CHARGE_PER_MIN * etaMinutes;
        double total = (BASE_FARE + distanceCharge + timeCharge) * surgeMultiplier;
        return Math.max(total, MINIMUM_FARE);
    }

    public static double calculateActualFare(RideType rideType, double distanceKm, int durationMinutes, double surgeMultiplier) {
        double distanceCharge = getDistanceRate(rideType) * distanceKm;
        double timeCharge = TIME_CHARGE_PER_MIN * durationMinutes;
        double total = (BASE_FARE + distanceCharge + timeCharge) * surgeMultiplier;
        return Math.max(total, MINIMUM_FARE);
    }

    private static double getDistanceRate(RideType rideType) {
        switch (rideType) {
            case PREMIUM:
                return PREMIUM_PER_KM;
            case XL:
                return XL_PER_KM;
            default:
                return ECONOMY_PER_KM;
        }
    }

    public static double calculateCancellationFee(boolean driverArrived) {
        return driverArrived ? 50.0 : 0.0;
    }
}

class PaymentGateway {
    public boolean processPayment(Customer customer, double amount) {
        if (!customer.hasSavedPaymentMethod()) {
            return false;
        }
        return Math.random() > 0.1;
    }
}
//platform admin
class PlatformAdmin {
    private final List<Trip> completedTrips = new ArrayList<>();
    private final List<Rating> ratings = new ArrayList<>();

    public double getSurgeMultiplier() {
        return 1.0 + Math.random() * 0.3;
    }

    public void recordTrip(Trip trip) {
        completedTrips.add(trip);
    }

    public void recordRating(Rating rating) {
        ratings.add(rating);
    }
}
//feedback column
class Rating {
    private final String ratingId;
    private final String tripId;
    private final RaterType raterType;
    private final int score;
    private final String comment;

    public Rating(String ratingId, String tripId, RaterType raterType, int score, String comment) {
        this.ratingId = ratingId;
        this.tripId = tripId;
        this.raterType = raterType;
        this.score = score;
        this.comment = comment;
    }
}
//enums
enum RideType {
    ECONOMY,
    PREMIUM,
    XL
}

enum TripStatus {
    REQUESTED,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

enum RaterType {
    CUSTOMER,
    DRIVER
}

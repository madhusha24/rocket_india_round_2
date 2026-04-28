Ride Sharing / Cab Booking System (Java)

This is a console-based ride booking system I built in Java to understand how real world apps like Uber or Ola actually work behind the scenes.

Instead of just booking a ride, the system handles everything from finding a driver to completing the trip, calculating fare, processing payment, and even collecting ratings.

 What this project does

* Lets a user request a ride with pickup & drop location
* Matches the user with an available driver
* Calculates fare based on distance, time, and surge pricing
* Simulates real scenarios like:

  * driver rejecting a ride
  * no driver available
  * payment failure
  * driver no-show
* Completes the trip and collects ratings from both sides
 How it works

1. Choose a customer
2. Enter pickup & drop
3. System estimates distance, ETA, and fare
4. Finds a driver
5. Driver accepts/rejects
6. Trip starts → completes
7. Payment happens
8. Ratings are given

---
What I focused on


* Used **OOP concepts** (classes, encapsulation, enums)
* Designed a **matching system** for drivers
* Built a **fare calculation model with surge pricing**
* Handled **edge cases** (no drivers, cancellations, payment retry)
* Simulated real-time behavior using randomness

Main components

* Main → handles user interaction
* Customer → stores user data & trip history
* Driver → manages driver status & earnings
* Trip → controls ride lifecycle
* MatchingEngine → finds and assigns drivers
* FareCalculationModel → calculates fare
* PaymentGateway → simulates payment
* PlatformAdmin → stores trips & ratings


 Fare logic 

* Base fare: ₹30
* Per km:

  * Economy → ₹12
  * Premium → ₹18
  * XL → ₹22
* Time charge: ₹2/min
* Surge: ~1.0x – 1.3x
* Minimum fare: ₹60



## 👩‍💻 Built by

Madhusha Harini

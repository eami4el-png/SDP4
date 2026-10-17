package com.parking;

import com.parking.facade.ChainRecord;
import com.parking.facade.ParkingAdminFacade;
import com.parking.facade.ParkingFacade;
import com.parking.facade.ParkingGarage;
import com.parking.facade.ParkingRejectedException;
import java.nio.file.Path;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        ParkingGarage garage = new ParkingGarage(Path.of("data"), 5);
        ParkingFacade facade = garage.facade();
        ParkingAdminFacade admin = garage.adminFacade();
        Scanner scanner = new Scanner(System.in);

        List<ChainRecord> recovered = admin.recoverUnfinished();
        if (!recovered.isEmpty()) {
            System.out.println("Recovered " + recovered.size() + " unfinished chain(s) from disk.");
        }

        while (true) {
            System.out.println("\n=== SMART PARKING GARAGE DEMO ===");
            System.out.println("1. Park a vehicle (A -> B -> C)");
            System.out.println("2. Park a vehicle, space sensor grid fails (B fails, A undone)");
            System.out.println("3. Park a vehicle, payment terminal fails (C fails, B then A undone)");
            System.out.println("4. Inspect persisted state");
            System.out.println("5. Exit");
            System.out.print("Choose: ");
            if (!scanner.hasNextLine()) {
                return;
            }
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> park(scanner, facade);
                case "2" -> {
                    admin.setSensorGridOnline(false);
                    try {
                        park(scanner, facade);
                    } finally {
                        admin.setSensorGridOnline(true);
                    }
                }
                case "3" -> {
                    admin.setPaymentTerminalOnline(false);
                    try {
                        park(scanner, facade);
                    } finally {
                        admin.setPaymentTerminalOnline(true);
                    }
                }
                case "4" -> System.out.println(admin.describeState());
                case "5" -> {
                    System.out.println("Bye!");
                    return;
                }
                default -> System.out.println("Invalid option");
            }
        }
    }

    private static void park(Scanner scanner, ParkingFacade facade) {
        System.out.print("Plate number (e.g. 123ABC02): ");
        String plate = scanner.nextLine();
        System.out.print("Hours to stay (1-24): ");
        int hours;
        try {
            hours = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Hours must be a number.");
            return;
        }
        try {
            ChainRecord chain = facade.parkVehicle(plate, hours);
            if (chain.getStatus() == ChainRecord.Status.COMPLETED) {
                System.out.println("OK: " + chain.getPlateNumber() + " is parked. Chain " + chain.getStatus());
            } else {
                System.out.println("FAILED and rolled back: " + chain.getFailureReason()
                        + ". Chain " + chain.getStatus());
            }
        } catch (ParkingRejectedException e) {
            System.out.println("Rejected, nothing was saved: " + e.getMessage());
        }
    }
}

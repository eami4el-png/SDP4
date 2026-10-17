package com.parking.gate;

import java.util.regex.Pattern;

/** Pure business logic: reads a raw plate and decides whether it is acceptable. */
public class PlateReader {
    private static final Pattern VALID = Pattern.compile("[A-Z0-9-]{4,12}");

    public String read(String rawPlate) {
        if (rawPlate == null) {
            throw new InvalidPlateException("Plate cannot be empty");
        }
        String plate = rawPlate.trim().toUpperCase();
        if (!VALID.matcher(plate).matches()) {
            throw new InvalidPlateException(
                    "Plate '" + rawPlate.trim() + "' is not valid (4-12 letters, digits or '-')");
        }
        return plate;
    }
}

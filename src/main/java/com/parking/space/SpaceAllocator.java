package com.parking.space;

import java.util.Collection;

/** Pure business logic: decides which spot is given out next. */
public class SpaceAllocator {

    public int nextFreeSpot(int capacity, Collection<Integer> occupied) {
        for (int spot = 1; spot <= capacity; spot++) {
            if (!occupied.contains(spot)) {
                return spot;
            }
        }
        throw new SpaceUnavailableException("Garage is full (" + capacity + " spots)");
    }
}

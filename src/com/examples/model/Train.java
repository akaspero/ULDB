package com.examples.model;

import java.util.ArrayList;
import java.util.List;

public class Train {

    private long id;
    private List<List<String>> seatsInCars = new ArrayList();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public List<List<String>> getSeatsInCars() {
        return seatsInCars;
    }

    public void setSeatsInCars(List<List<String>> seatsInCars) {
        this.seatsInCars = seatsInCars;
    }
}

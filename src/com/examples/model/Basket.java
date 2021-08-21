package com.examples.model;

import java.util.ArrayList;
import java.util.List;

public class Basket {

    private long id;
    private List<Apple> apples = new ArrayList();
    private List<String> collectors = new ArrayList();

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public List<Apple> getApples() {
        return apples;
    }

    public void setApples(List<Apple> apples) {
        this.apples = apples;
    }

    public List<String> getCollectors() {
        return collectors;
    }

    public void setCollectors(List<String> collectors) {
        this.collectors = collectors;
    }
}

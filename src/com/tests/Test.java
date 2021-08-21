package com.tests;

import com.ULDB;
import com.examples.model.Apple;
import com.examples.model.Basket;
import com.examples.model.Train;

import java.math.BigDecimal;
import java.util.ArrayList;

public class Test {

    public static void main(String... args) {
        simpleObjectTest();
        objectOfObjectTest();
        listTest();
        listInListTest();
        System.out.println("All tests passed");
    }

    private static void assertThat(boolean isValid) {
        if (!isValid) throw new AssertionError();
    }

    private static void simpleObjectTest() {
        ULDB.setFilename("tmp.txt");
        ULDB.loadData();

        Apple apple = new Apple();
        apple.setColor("GREEN");
        apple.setWeight(150);

        ULDB.saveOrUpdate(apple);

        assertThat(apple.getId() == 1);

        ULDB.clearData();
        ULDB.loadData();

        Apple newApple = ULDB.get(Apple.class, 1L);
        assertThat(newApple != null);
        assertThat(newApple.getId() == 1);
        assertThat(newApple.getColor().equals("GREEN"));
        assertThat(newApple.getWeight() == 150);

        ULDB.deleteAllData();
    }

    private static void objectOfObjectTest() {
        ULDB.setFilename("tmp.txt");
        ULDB.loadData();

        Apple apple = new Apple();
        apple.setColor("GREEN");
        apple.setWeight(150);

        Apple apple2 = new Apple();
        apple2.setColor("RED");
        apple2.setWeight(200);

        Basket basket = new Basket();
        basket.getApples().add(apple);
        basket.getApples().add(apple2);

        ULDB.saveOrUpdate(basket);
        ULDB.clearData();
        ULDB.loadData();
        
        Basket newBasket = ULDB.get(Basket.class, 1L);
        assertThat(newBasket != null);
        assertThat(newBasket.getApples().size() == 2);
        assertThat(newBasket.getApples().get(0).getId() == 1);
        assertThat(newBasket.getApples().get(0).getColor() == null);

        Apple newApple = ULDB.loadObject(newBasket.getApples().get(0));
        assertThat(newApple != null);
        assertThat(newApple.getId() == 1);
        assertThat(newApple.getColor().equals("GREEN"));
        assertThat(newApple.getWeight() == 150);

        ULDB.deleteAllData();
    }

    private static void listTest() {
        ULDB.setFilename("tmp.txt");
        ULDB.loadData();

        Basket basket = new Basket();
        basket.getCollectors().add("Adam");
        basket.getCollectors().add("Ewa");

        ULDB.saveOrUpdate(basket);
        ULDB.clearData();
        ULDB.loadData();

        Basket newBasket = ULDB.get(Basket.class, 1L);
        assertThat(newBasket != null);
        assertThat(newBasket.getCollectors().size() == 2);
        assertThat(newBasket.getCollectors().get(0).equals("Adam"));
        assertThat(newBasket.getCollectors().get(1).equals("Ewa"));

        ULDB.deleteAllData();
    }

    public static void listInListTest() {
        ULDB.setFilename("tmp.txt");
        ULDB.loadData();

        Train train = new Train();
        train.getSeatsInCars().add(new ArrayList<>());
        train.getSeatsInCars().get(0).add("Seat 1");

        ULDB.saveOrUpdate(train);
        ULDB.clearData();
        ULDB.loadData();

        Train newTrain = ULDB.get(Train.class, 1L);
        assertThat(newTrain != null);
        assertThat(newTrain.getSeatsInCars() != null);
        assertThat(newTrain.getSeatsInCars().isEmpty());

        ULDB.deleteAllData();
    }
}


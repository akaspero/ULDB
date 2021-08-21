package com.examples;

import com.ULDB;
import com.examples.model.Apple;

import java.util.List;

public class BasicUsageExample {

    public static void main(String[] args) {
        configuringDatabase();
        addingFirstObjectToDatabase();
        removingDatabaseFromMemoryToSimulateAppClose();
        loadingSavedObject();
        deletingDatabase();
    }

    static void configuringDatabase() {
        // Set database file location.
        ULDB.setFilename("tmp.txt");
    }

    static void addingFirstObjectToDatabase() {
        // Create object that will be saved into database.
        Apple apple = new Apple();
        apple.setColor("Green");
        apple.setWeight(100);

        // Add object to database, ULDB will save all data to file every time app uses this method or delete method.
        ULDB.saveOrUpdate(apple);
    }

    static void removingDatabaseFromMemoryToSimulateAppClose() {
        // This will clear all data from database memory and it won't affect database saved to file.
        ULDB.clearData();
    }

    static void loadingSavedObject() {
        // Load database from file, need to be used only once to use database with existing data.
        ULDB.loadData();

        // Get all objects of Apple class from database.
        List<Apple> allApples = ULDB.getAll(Apple.class);

        // We saved only one Apple so there is only one in list. Get it from list.
        Apple savedApple = allApples.get(0);

        // Apple object should have its basic fields loaded.
        System.out.println(savedApple.getColor());
        System.out.println(savedApple.getWeight());

        // If You know id of object, then You can get this object directly from database. Remember to never change id of object!
        long idOfApple = savedApple.getId();

        // Get Apple by id.
        Apple theSameApple = ULDB.get(Apple.class, idOfApple);

        // This is the same object.
        System.out.println(savedApple.equals(theSameApple));
    }

    static void deletingDatabase() {
        // Use this method to delete database from local drive and local memory, deleted data cannot be recovered.
        ULDB.deleteAllData();
    }
}

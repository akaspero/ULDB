package com.examples;

import com.ULDB;
import com.examples.model.Apple;
import com.examples.model.ApplePackage;

import java.math.BigDecimal;
import java.util.List;

public class ObjectInObjectExample {

    public static void main(String[] args) {
        configuringDatabase();
        addingObjectToDatabase();
        removingDatabaseFromMemoryToSimulateAppClose();
        loadingObjectOfObject();
        deletingDatabase();
    }

    static void configuringDatabase() {
        // Set database file location.
        ULDB.setFilename("tmp.txt");
    }

    static void addingObjectToDatabase() {
        // Create object that will be saved into database.
        Apple apple = new Apple();
        apple.setColor("Green");
        apple.setWeight(100);

        // Add object to database, ULDB will save all data to file every time app uses this method or delete method.
        ULDB.saveOrUpdate(apple);

        // We create new object that with apple as one of parameters
        ApplePackage packageWithApple = new ApplePackage();
        packageWithApple.setApple(apple);
        packageWithApple.setPrice(new BigDecimal(2.55));

        // We add package to database. This will add Apple object to database as well, if we did not added it manually.
        ULDB.saveOrUpdate(packageWithApple);
    }

    static void removingDatabaseFromMemoryToSimulateAppClose() {
        // This will clear all data from database memory and it won't affect database saved to file.
        ULDB.clearData();
    }

    static void loadingObjectOfObject() {
        // Load database from file, need to be used only once to use database with existing data.
        ULDB.loadData();

        // Get all objects of Packages class from database.
        List<ApplePackage> applePackages = ULDB.getAll(ApplePackage.class);

        // We saved only one Package so there is only one in list. Get it from list.
        ApplePackage applePackage = applePackages.get(0);

        // Package object should have its basic fields loaded.
        System.out.println("Price: " + applePackage.getPrice());

        // Get Apple object from Package object.
        Apple apple = applePackage.getApple();

        // Apple object is loaded, but not with all fields. ULDB objects of ULDB object's will have only id loaded.
        System.out.println("This is id object: " + apple.getId());

        // No other fields is loaded!
        System.out.println("This color should be null: " + applePackage.getApple().getColor());

        // Loading all fields of object with id.
        apple = ULDB.loadObject(apple);

        // Now other fields will be there.
        System.out.println("This color should not be null: " + apple.getColor());

        // You can also just get loaded object from database using its id.
        apple = ULDB.get(Apple.class, apple.getId());

        // Now fields should be there as well.
        System.out.println("This color should not be null: " + apple.getColor());
    }

    static void deletingDatabase() {
        // Use this method to delete database from local drive and local memory, deleted data cannot be recovered.
        ULDB.deleteAllData();
    }
}

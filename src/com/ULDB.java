package com;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class for saving objects.
 * Requirements:
 * - object have long field named "id" with getter "getId()" and setter "setId(long id)"
 * - you can't manipulate id
 * - each field you want to be saved need to have getter and setter
 * - children of object are saved if id of child is empty
 * Database is working with primitives, String, Calendar, LocalDate, LocalTime, BigDecimal, Lists.
 */
public class ULDB {

    private static int actionsSinceLastSave = 0;
    private static String fileName = "Data.txt";
    private static int actionLimitBeforeSaving = 0;
    private static String encoding = "UTF-8";

    private static final ConcurrentHashMap<String, HashMap<Long, Object>> storedData = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> lastId = new ConcurrentHashMap<>();

    /**
     * Add object to database or update existing one.
     * It saves all fields that are supported by ULDB and objects with id field that was not yet added to database.
     * Objects with id that are not in database will have its children added as well.
     *
     * @param obj object for adding
     * @return id of saved object or -1 if object is not valid for save (lack of id field).
     */
    public static long saveOrUpdate(Object obj) {
        Method getId = getIdMethodIfValidForSave(obj);
        if (getId == null) {
            return -1L;
        }
        long id = addToDatabase(obj, getId);

        try {
            Set<String> methodSet = new HashSet<>();
            for (Method method : obj.getClass().getMethods()) {
                methodSet.add(method.getName());
            }

            // looks for children that can be added to database
            for (Method method : obj.getClass().getMethods()) {
                String setterMethodName = getSetter(method.getName());
                if (setterMethodName == null || !methodSet.contains(setterMethodName)) {
                    continue;
                }
                if (method.getReturnType() == List.class || method.getReturnType() == ArrayList.class) {
                    Object returnObj = method.invoke(obj);
                    if (returnObj == null) {
                        continue;
                    }
                    for (Object listObject : (List) returnObj) {
                        // save object only if valid and it do not exists in database
                        Method getIdMethod = getIdMethodIfValidForSave(listObject.getClass());
                        if (getIdMethod == null) {
                            break;
                        }
                        if (getId(listObject) == 0) {
                            ULDB.saveOrUpdate(listObject);
                        }
                    }
                } else {
                    Method getIdMethod = getIdMethodIfValidForSave(method.getReturnType());
                    if (getIdMethod != null) {
                        Object returnObj = method.invoke(obj);
                        // save object only if it do not exists in database
                        if (getId(returnObj) == 0) {
                            ULDB.saveOrUpdate(returnObj);
                        }
                    }
                }
            }
        } catch (Exception e) {
            handleException(e);
        }
        saveDataIfNeeded();
        return id;
    }

    /**
     * Loads all fields of object. Children with id filed will have only id loaded.
     *
     * @param object object containing id
     * @return filled object with data or null if object is not valid for save
     */
    @SuppressWarnings("unchecked")
    public static <T> T loadObject(T object) {
        try {
            Method method = object.getClass().getMethod("getId");
            if (method == null) return null;
            long id = (long) method.invoke(object);
            return (T) ULDB.get(object.getClass(), id);
        } catch (Exception e) {
            handleException(e);
        }
        return null;
    }

    /**
     * Return object from database.
     *
     * @param objectClass class of searched object
     * @param id          id of object
     * @return object filled with data or null if object do not exist
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> objectClass, Long id) {
        if (storedData.containsKey(objectClass.getName()))
            return (T) storedData.get(objectClass.getName()).get(id);
        return null;
    }

    /**
     * Returns all objects of specified class from database.
     *
     * @param objectClass class of object
     * @return all objects of specified class
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> getAll(Class<T> objectClass) {
        List<T> list = new ArrayList<>();
        if (storedData.containsKey(objectClass.getName())) {
            for (Long id : storedData.get(objectClass.getName()).keySet()) {
                list.add((T) storedData.get(objectClass.getName()).get(id));
            }
        }
        return list;
    }

    /**
     * Deletes object from database.
     *
     * @param obj object for deletion
     * @return true if object deleted or false otherwise
     */
    public static boolean delete(Object obj) {
        try {
            if (obj == null)
                return false;

            Class<?> objectClass = obj.getClass();
            if (objectClass == null || !storedData.containsKey(objectClass.getName()))
                return false;

            Method idMethod = objectClass.getMethod("getId");
            if (idMethod == null)
                return false;

            long id = (long) idMethod.invoke(obj);

            if (storedData.get(objectClass.getName()).containsKey(id)) {
                storedData.get(objectClass.getName()).remove(id);
                saveDataIfNeeded();
                return true;
            }
        } catch (Exception e) {
            handleException(e);
        }
        return false;
    }

    /**
     * Loads all data from local drive.
     */
    public static void loadData() {
        try {
            File f = new File(fileName);
            if (!f.exists()) return;

            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF8"));
            String currentLine;

            while ((currentLine = br.readLine()) != null) {
                if (!currentLine.isEmpty()) {
                    if (currentLine.charAt(0) == '#') {
                        String[] data = currentLine.substring(1).split(":", -1);
                        String objectClass = data[0];
                        long id = Long.parseLong(data[1]);
                        lastId.put(objectClass, id);
                    } else {
                        addToDatabase(convertToObject(currentLine));
                    }
                }
            }
            br.close();

        } catch (Exception e) {
            handleException(e);
        }
    }

    /**
     * Manually saves all data to local drive.
     */
    public static void saveData() {
        StringBuilder sb = new StringBuilder();
        for (String objectClass : storedData.keySet()) {
            sb.append("#");
            sb.append(objectClass);
            sb.append(":");
            sb.append(lastId.get(objectClass));
            sb.append("\n");
            for (Long id : storedData.get(objectClass).keySet()) {
                Object object = storedData.get(objectClass).get(id);
                sb.append(convertObjectToString(object));
                sb.append("\n");
            }
        }

        try {
            Writer out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName), encoding));
            out.write(sb.toString());
            out.close();
            ULDB.actionsSinceLastSave = 0;
        } catch (Exception e) {
            handleException(e);
        }
    }

    /**
     * Sets the name of file where data should be stored.
     * Can contain path, but all folders should exist.
     *
     * @param fileName name of file
     */
    public static void setFilename(String fileName) {
        ULDB.fileName = fileName;
    }

    /**
     * Deletes all data from memory and local drive.
     */
    public static void deleteAllData() {
        try {
            File f = new File(fileName);
            f.delete();
        } catch (Exception e) {
            handleException(e);
        }
        clearData();
        loadData();
    }

    /**
     * Removes data from memory only. Data stored on local drive will not be touched here.
     */
    public static void clearData() {
        storedData.clear();
        lastId.clear();
        actionsSinceLastSave = 0;
    }

    /**
     * Set charset encoding for saved data. Default: UTF-8.
     *
     * @param encoding charset encoding
     */
    public static void setEncoding(String encoding) {
        ULDB.encoding = encoding;
    }

    /**
     * Sets how many actions could be done before ULDB saves data to local drive. Default is 0.
     * Put negative value to disable automatic save.
     * Making auto-save occur less often can increase performance, but can lead to loss of data when application is closed without running manual save.
     *
     * @param actionLimitBeforeSaving number of actions (actions: object save or object delete)
     */
    public static void setActionLimitBeforeSaving(int actionLimitBeforeSaving) {
        ULDB.actionLimitBeforeSaving = actionLimitBeforeSaving;
    }

    /**
     * Adds object to database without any validation.
     *
     * @param obj object to save
     * @return id of new object or -1 if object was not added to database
     */
    private static long addToDatabase(Object obj) {
        try {
            Method getIdMethod = obj.getClass().getMethod("getId");
            return addToDatabase(obj, getIdMethod);
        } catch (Exception e) {
            handleException(e);
        }
        return -1L;
    }

    /**
     * Add object to database. Run getIdMethodIfValidForSave before running this.
     *
     * @param obj      object to save
     * @param idMethod getter method for object id
     * @return id of new object
     */
    private static long addToDatabase(Object obj, Method idMethod) {
        long id = -1L;
        try {
            id = (long) idMethod.invoke(obj);

            Class<?> objectClass = obj.getClass();

            if (id < 1 || !isValidId(objectClass.getName(), id)) {
                id = generateId(objectClass.getName());

                Method setId = getIdSetterMethod(objectClass);
                if (setId != null) {
                    setId.invoke(obj, id);
                } else {
                    handleException(new Exception("'setId(long)' function missing!"));
                }
            }

            if (!storedData.containsKey(objectClass.getName())) {
                storedData.put(objectClass.getName(), new HashMap<>());
            }
            storedData.get(objectClass.getName()).put(id, obj);
        } catch (Exception e) {
            handleException(e);
        }
        return id;
    }

    /**
     * Converts object to string line.
     *
     * @param obj object to convert
     * @return Object as String
     */
    private static String convertObjectToString(Object obj) {
        StringBuilder objectStringBuilder = new StringBuilder();
        try {
            if (obj == null) {
                return null;
            }

            Class<?> objectClass = obj.getClass();
            Method idMethod = objectClass.getMethod("getId");
            Object id = idMethod.invoke(obj);

            objectStringBuilder.append(objectClass.getName());
            objectStringBuilder.append(";Id:");
            objectStringBuilder.append(id.toString());

            for (Method method : objectClass.getMethods()) {
                if (method.getReturnType() == null || method.equals(idMethod)) {
                    continue;
                }
                if (!method.getName().startsWith("get") && !method.getName().startsWith("is")) {
                    continue;
                }
                // do not save data that do not have setter
                if (!haveSetter(objectClass, method.getName())) {
                    continue;
                }

                Object returnObj = method.invoke(obj, (Object[]) null);
                if (returnObj == null) {
                    continue;
                }

                StringBuilder data = getDataAsString(returnObj);
                if (data == null) {
                    continue;
                }

                if (objectStringBuilder.length() > 0) {
                    objectStringBuilder.append(";");
                }

                objectStringBuilder.append(removePerfix(method));
                objectStringBuilder.append(":");
                objectStringBuilder.append(data);
            }
        } catch (Exception e) {
            handleException(e);
        }
        return objectStringBuilder.toString();
    }

    private static StringBuilder getDataAsString(Object returnObj) throws InvocationTargetException, IllegalAccessException {
        StringBuilder data = new StringBuilder();
        Class returnType = returnObj.getClass();
        Method tmpIdMethod;

        if (returnType.isEnum()) {
            Enum<?> e = (Enum<?>) returnObj;
            data.append(replaceOtherChars(e.name()));
        } else if (returnType == boolean.class || returnType == Boolean.class) {
            Boolean trueOrFalse = (Boolean) returnObj;
            if (trueOrFalse)
                data.append("1");
            else
                data.append("0");
        } else if (returnType == Integer.class || returnType == int.class
                || returnType == Short.class || returnType == short.class
                || returnType == Long.class || returnType == long.class
                || returnType == BigDecimal.class) {
            data.append(returnObj.toString());
        } else if (returnType == String.class) {
            data.append(replaceOtherChars(returnObj.toString()));
        } else if (returnType == List.class || returnType == ArrayList.class) {
            List<?> list = (List<?>) returnObj;
            if (list.size() == 0) {
                return null;
            }

            Object firstObject = list.get(0);
            // do not save list of other collections
            if (firstObject instanceof Collection) {
                return null;
            }

            Class parameterClass = firstObject.getClass();
            StringBuilder listData = new StringBuilder();

            for (Object listObject : (List<?>) returnObj) {
                if (parameterClass == null) {
                    parameterClass = listObject.getClass();
                }
                listData.append(",");
                Method getIdMethod = getIdMethodIfValidForSave(listObject);
                if (getIdMethod != null) {
                    Long listObjectId = (Long) getIdMethod.invoke(listObject, (Object[]) null);
                    listData.append(listObjectId);
                } else {
                    listData.append(getDataAsString(listObject));
                }
            }
            data.append(parameterClass.getName());
            data.append(listData);
        } else if (returnType == Calendar.class) {
            String sb = ((Calendar) returnObj).get(Calendar.YEAR) +
                    "." +
                    ((Calendar) returnObj).get(Calendar.MONTH) +
                    "." +
                    ((Calendar) returnObj).get(Calendar.DAY_OF_MONTH) +
                    "." +
                    ((Calendar) returnObj).get(Calendar.HOUR_OF_DAY) +
                    "." +
                    ((Calendar) returnObj).get(Calendar.MINUTE) +
                    "." +
                    ((Calendar) returnObj).get(Calendar.SECOND) +
                    "." +
                    ((Calendar) returnObj).get(Calendar.MILLISECOND);
            data.append(replaceOtherChars(sb));
        } else if (returnType == LocalDate.class) {
            data.append(((LocalDate) returnObj).toEpochDay());
        } else if (returnType == LocalDateTime.class) {
            data.append(((LocalDateTime) returnObj).toEpochSecond(ZoneOffset.UTC));
        } else if ((tmpIdMethod = getIdMethodIfValidForSave(returnType)) != null) {
            Long returnObjectId = (Long) tmpIdMethod.invoke(returnObj, (Object[]) null);
            data.append(returnObjectId);
        } else {
            return null;
        }
        return data;
    }

    private static boolean haveSetter(Class className, String methodName) {
        if (methodName.startsWith("get")) {
            String setterName = "set" + methodName.substring(3);
            return haveMethod(className, setterName);
        } else if (methodName.startsWith("is")) {
            String setterName = "set" + methodName.substring(2);
            return haveMethod(className, setterName);
        }
        return false;
    }

    /**
     * Converts object saved as string into normal object.
     *
     * @param objectAsString object saved as string
     * @return object with filled data
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convertToObject(String objectAsString) {
        if (objectAsString == null || objectAsString.isEmpty()) {
            return null;
        }
        String[] data = objectAsString.split(";", -1);

        Class<?> objectClass = null;
        HashMap<String, String> methodsWithValues = new HashMap<>();

        for (String s : data) {
            if (s == null || s.isEmpty()) {
                continue;
            }

            String[] field = s.split(":", -1);

            if (field.length < 1) {
                continue;
            }

            if (field.length == 1) { // class name
                try {
                    objectClass = Class.forName(field[0]);
                } catch (ClassNotFoundException e) {
                    handleException(e);
                    return null;
                }
            } else if (field.length == 2) { // data name and value
                methodsWithValues.put(addSetPrefix(field[0]), field[1]);
            }
        }

        if (objectClass == null)
            return null;

        // init new object
        Object object;
        try {
            object = objectClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            handleException(e);
            return null;
        }

        // filling fields of new object
        for (Method method : objectClass.getMethods()) {

            if (!methodsWithValues.containsKey(method.getName())) {
                continue;
            }

            String value = methodsWithValues.get(method.getName());

            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1) {
                continue;
            }

            Class<?> parameterClass = parameters[0];

            setValueToTargetObject(object, method, value, parameterClass);
        }
        return object;
    }

    @SuppressWarnings("unchecked")
    private static void setValueToTargetObject(Object targetObject, Method setterMethod, String value, Class parameterClass) {
        if (parameterClass.isEnum()) {
            try {
                Class<? extends Enum> enumClass = (Class<? extends Enum>) parameterClass;
                setterMethod.invoke(targetObject, Enum.valueOf(enumClass, value));
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                handleException(e);
            }
        } else if (parameterClass == Boolean.class) {
            try {
                switch (value) {
                    case "1":
                        setterMethod.invoke(targetObject, true);
                        break;
                    case "0":
                        setterMethod.invoke(targetObject, false);
                        break;
                    default:
                        setterMethod.invoke(targetObject, (Object) null);
                        break;
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                handleException(e);
            }
        } else if (parameterClass == boolean.class) {
            try {
                if ("1".equals(value)) {
                    setterMethod.invoke(targetObject, true);
                } else {
                    setterMethod.invoke(targetObject, false);
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                handleException(e);
            }
        } else if (parameterClass == Long.class || parameterClass == long.class) {
            try {
                setterMethod.invoke(targetObject, Long.parseLong(value));
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                handleException(e);
            }
        } else if (parameterClass == Integer.class || parameterClass == int.class) {
            try {
                setterMethod.invoke(targetObject, Integer.parseInt(value));
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                handleException(e);
            }
        } else if (parameterClass == Short.class || parameterClass == short.class) {
            try {
                setterMethod.invoke(targetObject, Short.parseShort(value));
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                handleException(e);
            }
        } else if (parameterClass == Double.class || parameterClass == double.class) {
            try {
                setterMethod.invoke(targetObject, Double.parseDouble(value));
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                handleException(e);
            }
        } else if (parameterClass == String.class) {
            try {
                setterMethod.invoke(targetObject, replaceOtherCharsReverted(value));
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                handleException(e);
            }
        } else if (parameterClass == BigDecimal.class) {
            try {
                if (value.contains(".")) {
                    setterMethod.invoke(targetObject, BigDecimal.valueOf(Double.parseDouble(value)));
                } else {
                    setterMethod.invoke(targetObject, BigDecimal.valueOf(Long.parseLong(value)));
                }
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                handleException(e);
            }
        } else if (parameterClass == ArrayList.class || parameterClass == List.class) {
            List list = new ArrayList<>();
            String[] dataStringArray = value.split(",");
            Class collectionElementClass;
            try {
                collectionElementClass = Class.forName(dataStringArray[0]);
            } catch (Exception e) {
                handleException(e);
                return;
            }
            for (int i = 1; i < dataStringArray.length; i++) {
                try {
                    Method setIdMethod;
                    setIdMethod = getIdSetterMethod(collectionElementClass);
                    if (setIdMethod != null) {
                        Long generatedId = Long.parseLong(replaceOtherCharsReverted(dataStringArray[i]));
                        Object newObject = collectionElementClass.newInstance();
                        setIdMethod.invoke(newObject, generatedId);
                        list.add(newObject);
                    } else {
                        Method listAddMethod = parameterClass.getMethod("add", Object.class);
                        setValueToTargetObject(list, listAddMethod, dataStringArray[i], collectionElementClass);
                    }
                } catch (Exception e) {
                    handleException(e);
                }
            }
            try {
                setterMethod.invoke(targetObject, list);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                handleException(e);
            }
        } else if (parameterClass == Calendar.class) {
            String dataString = replaceOtherCharsReverted(value);
            String[] dataStringArray = dataString.split("\\.");
            Calendar date = GregorianCalendar.getInstance();
            date.set(Calendar.YEAR, Integer.parseInt(dataStringArray[0]));
            date.set(Calendar.MONTH, Integer.parseInt(dataStringArray[1]));
            date.set(Calendar.DAY_OF_MONTH, Integer.parseInt(dataStringArray[2]));
            date.set(Calendar.HOUR_OF_DAY, Integer.parseInt(dataStringArray[3]));
            date.set(Calendar.MINUTE, Integer.parseInt(dataStringArray[4]));
            date.set(Calendar.SECOND, Integer.parseInt(dataStringArray[5]));
            date.set(Calendar.MILLISECOND, Integer.parseInt(dataStringArray[6]));
            try {
                setterMethod.invoke(targetObject, date);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                handleException(e);
            }
        } else if (parameterClass == LocalDate.class) {
            long epochDay = Long.parseLong(value);
            LocalDate date = LocalDate.ofEpochDay(epochDay);
            try {
                setterMethod.invoke(targetObject, date);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                handleException(e);
            }
        } else if (parameterClass == LocalDateTime.class) {
            long epochSecond = Long.parseLong(value);
            LocalDateTime dateAndTime = LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC);
            try {
                setterMethod.invoke(targetObject, dateAndTime);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                handleException(e);
            }
        } else {
            try {
                Method getIdMethod = getIdMethodIfValidForSave(parameterClass);

                if (getIdMethod != null) {
                    Object newObject = parameterClass.newInstance();
                    Method setIdMethod = parameterClass.getMethod("setId", long.class);
                    if (setIdMethod != null) {
                        Long id = Long.parseLong(replaceOtherCharsReverted(value));
                        setIdMethod.invoke(newObject, id);
                        setterMethod.invoke(targetObject, newObject);
                    }
                }
            } catch (Exception e) {
                handleException(e);
            }
        }
    }

    /**
     * Returns setter method for id or null if such do not exists.
     */
    @SuppressWarnings("unchecked")
    private static Method getIdSetterMethod(Class objectClass) {
        try {
            return objectClass.getMethod("setId", long.class);
        } catch (NoSuchMethodException ex) {
            return null;
        }
    }

    /**
     * Generate new database id for object.
     *
     * @param objectClass object class
     * @return next id that should be used for that class
     */
    private static long generateId(String objectClass) {
        long id = 1;
        if (lastId.containsKey(objectClass)) {
            id = lastId.get(objectClass) + 1;
            while (storedData.get(objectClass) != null && storedData.get(objectClass).containsKey(id)) {
                id++;
            }
        }
        lastId.put(objectClass, id);

        return id;
    }

    private static boolean isValidId(String objectClass, Long id) {
        if (!lastId.containsKey(objectClass)) {
            return false;
        }
        long last = lastId.get(objectClass);
        return last >= id;
    }

    /**
     * Remove 'get' or 'is' prefix to get field name.
     *
     * @param method Method
     * @return name of field
     */
    private static String removePerfix(Method method) {
        String methodName = method.getName();
        if (methodName.startsWith("get")) {
            methodName = methodName.replaceFirst("get", "");
        } else if (methodName.startsWith("is")) {
            methodName = methodName.replaceFirst("is", "");
        }
        return methodName;
    }

    /**
     * Adds 'set' prefix to get setter method name.
     *
     * @return setter method name
     */
    private static String addSetPrefix(String s) {
        return "set" + s;
    }

    //TODO temporary solution, could be done better for sure
    private static String replaceOtherChars(String s) {
        s = s.replaceAll("#", "XaFS");
        s = s.replaceAll(":", "#x#");
        s = s.replaceAll(";", "#y#");
        s = s.replaceAll("\n", "#n#");
        s = s.replaceAll("\t", "#t#");
        s = s.replaceAll("<", "#1#");
        s = s.replaceAll(">", "#2#");
        s = s.replaceAll("&", "#l#");
        return s;
    }

    private static String replaceOtherCharsReverted(String s) {
        s = s.replaceAll("#x#", ":");
        s = s.replaceAll("#y#", ";");
        s = s.replaceAll("#n#", "\n");
        s = s.replaceAll("#t#", "\t");
        s = s.replaceAll("#1#", "<");
        s = s.replaceAll("#2#", ">");
        s = s.replaceAll("#l#", "&");
        s = s.replaceAll("XaFS", "#");
        return s;
    }

    /**
     * Check does object is valid for save by ULDB.
     *
     * @param object Class of object for validation
     * @return method for getting id of object
     */
    private static Method getIdMethodIfValidForSave(Object object) {
        if (object == null) {
            return null;
        }

        return getIdMethodIfValidForSave(object.getClass());
    }

    /**
     * Check does class is valid for save by ULDB.
     *
     * @param objectClass Class of object for validation
     * @return method for getting id of object
     */
    private static Method getIdMethodIfValidForSave(Class<?> objectClass) {

        if (objectClass == null)
            return null;

        boolean hasSetMethod = false;
        boolean hasGetMethod = false;
        Method getIdMethod = null;

        for (Method method : objectClass.getMethods()) {
            if (method.getName().equals("getId")) {
                if (method.getReturnType() != null && method.getReturnType() == long.class || method.getReturnType() == Long.class) {
                    getIdMethod = method;
                    hasGetMethod = true;
                }
            } else if (method.getName().equals("setId")) {
                if (method.getParameterTypes() != null && method.getParameterTypes().length == 1
                        && (method.getParameterTypes()[0] == Long.class || method.getParameterTypes()[0] == long.class)) {
                    hasSetMethod = true;
                }
            }
            if (hasSetMethod && hasGetMethod) {
                return getIdMethod;
            }
        }
        return null;
    }

    private static boolean haveMethod(Class<?> objectClass, String methodName) {
        for (Method m : objectClass.getMethods()) {
            if (m.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets id of object.
     *
     * @param obj checked object
     * @return id of object
     */
    private static long getId(Object obj) {
        try {
            Method idMethod = obj.getClass().getMethod("getId");
            return (long) idMethod.invoke(obj);
        } catch (Exception e) {
            handleException(e);
        }
        return 0;
    }

    /**
     * Return setter method name of getter name.
     */
    private static String getSetter(String s) {
        if (s.startsWith("get")) {
            return "set" + s.substring(3);
        }
        if (s.startsWith("is")) {
            return "set" + s.substring(2);
        }
        return null;
    }

    /**
     * Saves data if unsaved action counter is have bigger value then actionLimitBeforeSaving (default 0).
     * Negative value of actionLimitBeforeSaving will disable automatic save.
     */
    private static void saveDataIfNeeded() {
        if (actionLimitBeforeSaving < 0) {
            return;
        }

        actionsSinceLastSave++;
        if (actionsSinceLastSave > actionLimitBeforeSaving) {
            saveData();
        }
    }

    /**
     * Exception handling method. Change this code or override this method to use custom made exception handling.
     *
     * @param e Exception
     */
    private static void handleException(Exception e) {
        e.printStackTrace();
    }
}

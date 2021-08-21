Author: Andrzej Kasperowicz


Ultra Light Data Base
One-file, easy to use local database.


How to use:
- Copy ULDB.java file to your project and edit package definition for file location if needed.
- All public methods are static and can be used without any initialization or configuration.
- All objects that have id field (must be primitive long type) with getter and setter can be saved into database (see example).
- Never manually change id value of object.
- Database will save all fields of object that have getter and setter following Java naming convention (eg. setXxx, getXxx or isXxx).
- Supported types: int, Integer, long, Long, short, Short, String, boolean, Boolean, Enum, BigDecimal, List*, ArrayList*, Calendar, LocalDate, LocalDateTime.
- Saved object can have other ULDB object as field. ULDB will only save id of that object so be sure to save it manually using `saveOrUpdate`. Loaded from database object of object need to be loaded as well (use `loadObject`).

(*) collection of other collections will not be saved.

Methods description:
- `loadData` - loads all data from local drive. Need to be run first to use already saved data.
- `saveOrUpdate` - adds object to database or updates existing one.


Configuration:
- `setFilename` - sets database file name and path (path need to exists).
- `setEncoding` - sets encoding for saved data.
- `setActionLimitBeforeSaving` - sets number of actions (object save or object delete) before ULDB saves data to local drive. Default is 0. Put negative value to disable automatic save. Making autosave occur less often can increase performance, but can lead to loss of data when application is closed without running manual save.


TODO:
- Add support for byte, LocalTime, Date.
- Finish documentation.

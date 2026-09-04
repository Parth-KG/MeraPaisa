# Room and Glance ship their own consumer rules, and nothing in this app reflects over its
# own classes, so the defaults in proguard-android-optimize.txt cover it. Two exceptions:

# Entities and DAOs are referenced by generated Room code and by column name.
-keep class com.kg.merapaisa.data.Person { *; }
-keep class com.kg.merapaisa.data.Transaction { *; }
-keep class com.kg.merapaisa.data.PersonWithBalance { *; }

# org.json is part of the platform; the rate response is parsed by key, not by field name.
-dontwarn org.json.**

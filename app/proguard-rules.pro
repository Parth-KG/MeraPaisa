# Room and Glance ship their own consumer rules, and nothing in this app reflects over its
# own classes, so the defaults in proguard-android-optimize.txt cover it. One exception:

# org.json is part of the platform; the update check reads GitHub's reply with it, by key.
-dontwarn org.json.**

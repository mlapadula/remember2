# Remember2

An in-memory data store backed by shared preferences, for Android.

This is a key-value store with some nice properties:

1. Speed. Everything is loaded into memory so reads can happen on the UI thread. Writes and deletes happen asynchronously (with callbacks). Every public method is safe to call from the UI thread.

2. Durability. Writes get persisted to disk, so that this store maintains state even if the app closes or is killed.

3. Consistency. Doing a write followed by a read should return the value you just put.

4. Thread-safety. Reads and writes can happen from anywhere without the need for external synchronization.

Note that since writes are asynchronous, an in-flight write may be lost if the app is killed before the data has been written to disk. If you require true 'commit' semantics then Remember is not for you.

## About Remember2

This is an update of the [original Remember library](https://github.com/tumblr/remember) with some extra features:

* Namespacing by file, so you can effectively have different "tables"
* Allowing you to store/retrieve JSON easily
* Allowing you to query the data store. (In the easiest, simplest way possible: iterate over the values and look for things that match a given function)
* Adds some missing methods
* Fails fast on null insert

Everything is still in-memory so it's fast and easy, but not appropriate for storing really large amounts of data.

Remember2 is backwards-compatible with the original Remember. Just create an instance of Remember2 that references the same shared preferences file.

## Download

Grab the artifact via JCenter. Include JCenter as a repository in your build.gradle file:

```groovy
repositories {
    jcenter()
}
```

And add Remember to your dependencies:

```groovy
dependencies {
    compile (group: 'com.mlapadula', name: 'remember2', version: '2.0.0', ext: 'aar')
}
```

## Usage

Obtain instances of Remember via `create()`:

```java
Remember myRemember = Remember.create(context, "some-shared-preferences-name");
```

This will create a new instance of Remember if you haven't used it before, or will use a shared instance if there's already one in existence.

Use Remember like so:

```java
myRemember.putString("some key", "some value");
String value = myRemember.getString("some key", "");
```

More examples are available in the [sample app](https://github.com/mlapadula/remember2/blob/master/sample-app/src/main/java/com/mlapadula/remembersample/RememberSample.java#L56)

## Javadoc

Right [here](https://cdn.rawgit.com/mlapadula/remember2/master/doc/index.html)

## Sample app
Clone and build this repo in Android Studio to see an example of a sample app. The app simply increments a counter stored in Remember and tells you the value.

## Contact

Michael Lapadula: mlapadula@gmail.com

## License

```
Copyright 2016 Michael Lapadula

Licensed under the Apache License, Version 2.0 (the “License”); you may not use this file except in compliance with the License. You may obtain a copy of the License at apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an “AS IS” BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
```

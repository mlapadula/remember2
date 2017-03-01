package com.mlapadula.remember2;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * An in-memory data store backed by shared preferences. This is a key-value store with a few important properties:
 * <br><br>
 * 1) Speed. Everything is stored in-memory so reads can happen on the UI thread. Writes and deletes happen
 * asynchronously (with callbacks). Every public method is safe to call from the UI thread.
 * <br><br>
 * 2) Durability. Writes get persisted to disk, so that this store maintains state even if the app closes or is killed.
 * <br><br>
 * 3) Consistency. Doing a write followed by a read should return the value you just put.
 * <br><br>
 * 4) Thread-safety. Reads and writes can happen from anywhere without the need for external synchronization.
 * <br><br>
 * Note that since writes are asynchronous, an in-flight write may be lost if the app is killed before the data has
 * been written to disk. If you require true 'commit' semantics then Remember is not for you.
 * <br><br>
 * Created by Michael Lapadula (https://github.com/mlapadula)
 */
public class Remember {

    private static final String TAG = Remember.class.getSimpleName();

    /**
     * Cache of {@link Remember} instances. This ensures that we don't have more than one instance
     * of Remember backed by the same shared prefs file.
     */
    private static final ConcurrentMap<String,WeakReference<Remember>> sCachedInstances =
            new ConcurrentHashMap<>();

    /**
     * Lock to ensure that only one disk write happens at a time.
     */
    private final Object SHARED_PREFS_LOCK = new Object();

    /**
     * The {@link SharedPreferences} instance
     */
    private SharedPreferences mSharedPreferences;

    /**
     * Our data. This is a write-through cache of the data we're storing in SharedPreferences.
     */
    private ConcurrentMap<String, Object> mData;

    /**
     * Constructor.
     */
    private Remember(Context context, String sharedPrefsName) {
        long start = SystemClock.uptimeMillis();

        // Read from shared prefs
        mSharedPreferences = context.getSharedPreferences(sharedPrefsName, Context.MODE_PRIVATE);
        mData = new ConcurrentHashMap<String, Object>();

        // Remove the item if it is null, for backward compatible
        for (Map.Entry<String, ?> entry : mSharedPreferences.getAll().entrySet())
            if (entry.getValue() != null)
                mData.put(entry.getKey(), entry.getValue());

        long delta = SystemClock.uptimeMillis() - start;
        Log.i(TAG, "Remember took " + delta + " ms to init");
    }

    /**
     * Creates a new instance of {@link Remember}
     *
     * @param context         the context to use. Using the application context is fine here.
     * @param sharedPrefsName the name of the shared prefs file to use
     * @return the instance that was initialized, or a cached instance if we already have one
     * that uses this shared preferences file
     */
    public static synchronized Remember create(Context context, String sharedPrefsName) {
        // Defensive checks
        if (context == null || TextUtils.isEmpty(sharedPrefsName)) {
            throw new RuntimeException(
                    "You must provide a valid context and shared prefs name when initializing Remember");
        }

        // Use existing instance if we already have one for this file
        WeakReference<Remember> cached = sCachedInstances.get(sharedPrefsName);
        if (cached != null) {
            Remember remember = cached.get();
            if (remember != null) {
                return remember;
            }
        }

        // Else, create a new instance and cache it
        Remember remember = new Remember(context, sharedPrefsName);
        WeakReference<Remember> weakRef = new WeakReference<Remember>(remember);
        sCachedInstances.put(sharedPrefsName, weakRef);
        return remember;
    }

    /**
     * Saves the given (key,value) pair to disk.
     *
     * @return true if the save-to-disk operation succeeded
     */
    private boolean saveToDisk(final String key, final Object value) {
        boolean success = false;
        synchronized (SHARED_PREFS_LOCK) {
            // Save it to disk
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            boolean didPut = true;
            if (value instanceof Float) {
                editor.putFloat(key, (Float) value);

            } else if (value instanceof Integer) {
                editor.putInt(key, (Integer) value);

            } else if (value instanceof Long) {
                editor.putLong(key, (Long) value);

            } else if (value instanceof String) {
                editor.putString(key, (String) value);

            } else if (value instanceof Boolean) {
                editor.putBoolean(key, (Boolean) value);

            } else {
                didPut = false;
            }

            if (didPut) {
                success = editor.commit();
            }
        }

        return success;
    }

    /**
     * Saves the given (key,value) pair to memory and (asynchronously) to disk.
     *
     * @param key      the key
     * @param value    the value to put. This MUST be a type supported by SharedPreferences. Which is to say: one of (float,
     *                 int, long, String, boolean).
     * @param callback the callback to fire. The callback will be fired on the UI thread,
     *                 and will be passed 'true' if successful, 'false' if not.
     * @return this instance
     */
    private <T> Remember saveAsync(final String key, final T value, final Callback callback) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("Trying to put a null key or value");
        }

        // Skip saveToDisk if value is the same
        if (mData.get(key) != null && mData.get(key).equals(value)) {
            // Fire the callback
            if (callback != null) {
                callback.apply(true);
            }
            return this;
        }

        // Put it in memory
        mData.put(key, value);

        // Save it to disk
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return saveToDisk(key, value);
            }

            @Override
            protected void onPostExecute(Boolean success) {
                // Fire the callback
                if (callback != null) {
                    callback.apply(success);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        return this;
    }

    /**
     * Gets the size of this collection.
     *
     * @return the size of this collection
     */
    public int size() {
        return mData.size();
    }

    /**
     * Returns a {@link Set} view of the keys contained. Note that since this Set is a view of
     * the underlying ConcurrentMap, the Set will have similar concurrency semantics. Ie,
     * The view's iterator is a "weakly consistent" iterator that will never throw
     * ConcurrentModificationException, and guarantees to traverse elements as they existed upon
     * construction of the iterator, and may (but is not guaranteed to) reflect any modifications
     * subsequent to construction.
     *
     * @return a {@link Set} view of keys contained
     */
    public Set<String> keys() {
        return mData.keySet();
    }

    /**
     * Queries for entries that match the given predicate, returning the corresponding keys.
     * This is an O(n) operation on the contained map.
     *
     * @param predicate the predicate to apply to values
     * @return the {@link Set} of keys for the matching entries
     */
    public Set<String> query(Predicate predicate) {
        Set<String> matches = new HashSet<>();
        Set<String> keys = keys();
        for (String key : keys) {
            Object value = mData.get(key);
            if (value != null && predicate.match(value)) {
                matches.add(key);
            }
        }
        return matches;
    }

    /**
     * Clears all data from this store.
     */
    public void clear() {
        clear(null);
    }

    /**
     * Clears all data from this store.
     *
     * @param callback the callback to fire when done. The callback will be fired on the UI thread,
     *                 and will be passed 'true' if successful, 'false' if not.
     */
    public void clear(final Callback callback) {
        mData.clear();
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                synchronized (SHARED_PREFS_LOCK) {
                    SharedPreferences.Editor editor = mSharedPreferences.edit();
                    editor.clear();
                    return editor.commit();
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (callback != null) {
                    callback.apply(success);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Removes the mapping indicated by the given key.
     */
    public void remove(String key) {
        remove(key, null);
    }

    /**
     * Removes the mapping indicated by the given key.
     *
     * @param callback the callback to fire when done. The callback will be fired on the UI thread,
     *                 and will be passed 'true' if successful, 'false' if not.
     */
    public void remove(final String key, final Callback callback) {
        mData.remove(key);
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                synchronized (SHARED_PREFS_LOCK) {
                    SharedPreferences.Editor editor = mSharedPreferences.edit();
                    editor.remove(key);
                    return editor.commit();
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (callback != null) {
                    callback.apply(success);
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Put a float. This saves to memory immediately and saves to disk asynchronously.
     */
    public Remember putFloat(final String key, final float value) {
        return saveAsync(key, value, null);
    }

    /**
     * Put an int. This saves to memory immediately and saves to disk asynchronously.
     */
    public Remember putInt(String key, int value) {
        return saveAsync(key, value, null);
    }

    /**
     * Put a long. This saves to memory immediately and saves to disk asynchronously.
     */
    public Remember putLong(String key, long value) {
        return saveAsync(key, value, null);
    }

    /**
     * Put a String. This saves to memory immediately and saves to disk asynchronously.
     */
    public Remember putString(String key, String value) {
        return saveAsync(key, value, null);
    }

    /**
     * Put a boolean. This saves to memory immediately and saves to disk asynchronously.
     */
    public Remember putBoolean(String key, boolean value) {
        return saveAsync(key, value, null);
    }

    /**
     * Put a {@link JSONObject}. This saves to memory immediately and saves to disk asynchronously.
     */
    public Remember putJsonObject(String key, JSONObject value) {
        return putJsonObject(key, value, null);
    }

    /**
     * Put a {@link JSONArray}. This saves to memory immediately and saves to disk asynchronously.
     */
    public Remember putJsonArray(String key, JSONArray value) {
        return putJsonArray(key, value, null);
    }
    /**
     * Put a float. This saves to memory immediately and saves to disk asynchronously.
     *
     * @param callback the callback to fire when done. The callback will be fired on the UI thread,
     *                 and will be passed 'true' if successful, 'false' if not.
     */
    public Remember putFloat(final String key, final float value, final Callback callback) {
        return saveAsync(key, value, callback);
    }

    /**
     * Put an int. This saves to memory immediately and saves to disk asynchronously.
     *
     * @param callback the callback to fire when done. The callback will be fired on the UI thread,
     *                 and will be passed 'true' if successful, 'false' if not.
     */
    public Remember putInt(String key, int value, final Callback callback) {
        return saveAsync(key, value, callback);
    }

    /**
     * Put a long. This saves to memory immediately and saves to disk asynchronously.
     *
     * @param callback the callback to fire when done. The callback will be fired on the UI thread,
     *                 and will be passed 'true' if successful, 'false' if not.
     */
    public Remember putLong(String key, long value, final Callback callback) {
        return saveAsync(key, value, callback);
    }

    /**
     * Put a String. This saves to memory immediately and saves to disk asynchronously.
     *
     * @param callback the callback to fire when done. The callback will be fired on the UI thread,
     *                 and will be passed 'true' if successful, 'false' if not.
     */
    public Remember putString(String key, String value, final Callback callback) {
        return saveAsync(key, value, callback);
    }

    /**
     * Put a boolean. This saves to memory immediately and saves to disk asynchronously.
     *
     * @param callback the callback to fire when done. The callback will be fired on the UI thread,
     *                 and will be passed 'true' if successful, 'false' if not.
     */
    public Remember putBoolean(String key, boolean value, final Callback callback) {
        return saveAsync(key, value, callback);
    }

    /**
     * Put a {@link JSONObject}. This saves to memory immediately and saves to disk asynchronously.
     *
     * @param callback the callback to fire when done. The callback will be fired on the UI thread,
     *                 and will be passed 'true' if successful, 'false' if not.
     */
    public Remember putJsonObject(String key, JSONObject value, final Callback callback) {
        String jsonString = value == null ? null : value.toString();
        return putString(key, jsonString, callback);
    }

    /**
     * Put a {@link JSONArray}. This saves to memory immediately and saves to disk asynchronously.
     *
     * @param callback the callback to fire when done. The callback will be fired on the UI thread,
     *                 and will be passed 'true' if successful, 'false' if not.
     */
    public Remember putJsonArray(String key, JSONArray value, final Callback callback) {
        String jsonString = value == null ? null : value.toString();
        return putString(key, jsonString, callback);
    }

    /**
     * Gets a float with the given key. Defers to the fallback value if the mapping didn't exist,
     * wasn't a float, or was null.
     */
    public float getFloat(String key, float fallback) {
        Float value = get(key, Float.class);
        return value != null ? value : fallback;
    }

    /**
     * Gets an int with the given key. Defers to the fallback value if the mapping didn't exist,
     * wasn't an int, or was null.
     */
    public int getInt(String key, int fallback) {
        Integer value = get(key, Integer.class);
        return value != null ? value : fallback;
    }

    /**
     * Gets a long with the given key. Defers to the fallback value if the mapping didn't exist,
     * wasn't a long, or was null.
     */
    public long getLong(String key, long fallback) {
        Long value = get(key, Long.class);
        return value != null ? value : fallback;
    }

    /**
     * Gets a String with the given key. Defers to the fallback value if the mapping didn't exist,
     * wasn't a String, or was null.
     */
    public String getString(String key, String fallback) {
        String value = get(key, String.class);
        return value != null ? value : fallback;
    }

    /**
     * Gets a boolean with the given key. Defers to the fallback value if the mapping didn't exist,
     * wasn't a boolean, or was null.
     */
    public boolean getBoolean(String key, boolean fallback) {
        Boolean value = get(key, Boolean.class);
        return value != null ? value : fallback;
    }

    /**
     * Gets the value at the given key, parsed as a {@link JSONObject}. Defers to the fallback
     * value if the mapping didn't exist or wasn't parseable as a {@link JSONObject}.
     */
    public JSONObject getJsonObject(String key, JSONObject fallback) {
        String jsonString = getString(key, null);
        if (!TextUtils.isEmpty(jsonString)) {
            try {
                return new JSONObject(jsonString);
            } catch (JSONException err) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * Gets the value at the given key, parsed as a {@link JSONArray}. Defers to the fallback
     * value if the mapping didn't exist or wasn't parseable as a {@link JSONArray}.
     */
    public JSONArray getJsonArray(String key, JSONArray fallback) {
        String jsonString = getString(key, null);
        if (!TextUtils.isEmpty(jsonString)) {
            try {
                return new JSONArray(jsonString);
            } catch (JSONException err) {
                return fallback;
            }
        }
        return fallback;
    }

    /**
     * Determines if we have a mapping for the given key.
     *
     * @return true if we have a mapping for the given key
     */
    public boolean containsKey(String key) {
        return mData.containsKey(key);
    }

    /**
     * Gets the value mapped by the given key, casted to the given class. If the value doesn't
     * exist or isn't of the right class, return null instead.
     */
    private <T> T get(String key, Class<T> clazz) {
        Object value = mData.get(key);
        T castedObject = null;
        if (clazz.isInstance(value)) {
            castedObject = clazz.cast(value);
        }
        return castedObject;
    }

    /**
     * Predicate for querying
     */
    public interface Predicate {
        /**
         * Determines if the given object is a match or not.
         *
         * @param obj the object to match on
         * @return true if it's a match, false otherwise
         */
        boolean match(Object obj);
    }

    /**
     * Callback for async operations.
     */
    public interface Callback {

        /**
         * Triggered after the async operation is completed.
         *
         * @param success true if saved successfully, false otherwise.
         */
        void apply(Boolean success);
    }

}

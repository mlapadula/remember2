package com.mlapadula.remembersample;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.mlapadula.remember2.Remember;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * A simple activity that counts how many times it's been resumed via {@link Remember}.
 */
public class RememberSample extends AppCompatActivity {

    private static final String PREFS_NAME = "com.remember.example";
    private static final String KEY = "test_key";

    private static Remember sRemember;

    private static Remember getRemember(Context context) {
        if (sRemember == null) {
            sRemember = Remember.create(context, PREFS_NAME);
        }
        return sRemember;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_remember_sample);
    }

    @Override
    protected void onResume() {
        super.onResume();

        int howMany = getRemember(this).getInt(KEY, 1);

        TextView textView = (TextView) findViewById(R.id.text_view);
        String testString = getApplicationContext().getResources().getQuantityString(R.plurals.youve_resumed, howMany,
                howMany);
        textView.setText(testString);

        getRemember(this).putInt(KEY, howMany + 1);

        // Some other simple examples:
        getRemember(this).putFloat("test-float", 123.0f);
        getRemember(this).putString("test-string", "hello world!");
        getRemember(this).putBoolean("test-boolean", true);
        getRemember(this).putLong("test-long", 54321L);

        Log.d("Remember", "put float: " + getRemember(this).getFloat("test-float", 0f));
        Log.d("Remember", "put string: " + getRemember(this).getString("test-string", ""));
        Log.d("Remember", "put boolean: " + getRemember(this).getBoolean("test-boolean", false));
        Log.d("Remember", "put long: " + getRemember(this).getLong("test-long", 0L));

        // JSON object example, with callback:
        JSONObject jsonObj = new JSONObject();
        try {
            jsonObj.put("some-json-key", "some-json-value");
        } catch (JSONException err) {
            // Don't care
        }
        getRemember(this).putJsonObject("test-json-object", jsonObj, new Remember.Callback() {
            @Override
            public void apply(Boolean success) {
                Log.d("Remember", "put json object: " + getRemember(RememberSample.this).getJsonObject("test-json-object", null));
            }
        });

        // JSON array example, with callback:
        JSONArray jsonArr = new JSONArray();
        jsonArr.put(1);
        jsonArr.put(2);
        jsonArr.put(3);
        getRemember(this).putJsonArray("test-json-array", jsonArr, new Remember.Callback() {
            @Override
            public void apply(Boolean success) {
                Log.d("Remember", "put json array: " + getRemember(RememberSample.this).getJsonArray("test-json-array", null));
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_remember_sample, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

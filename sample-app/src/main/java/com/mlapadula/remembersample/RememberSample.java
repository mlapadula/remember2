package com.mlapadula.remembersample;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.mlapadula.remember2.Remember;

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

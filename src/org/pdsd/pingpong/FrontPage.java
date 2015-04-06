package org.pdsd.pingpong;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import org.pdsd.pingpong.service.PingPongService;

public class FrontPage extends Activity {
    private PingPongService service;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Button update = (Button) findViewById(R.id.ok_button);

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EditText idBox = (EditText) findViewById(R.id.identity_box);
                String newDevId = idBox.getText().toString();
                changeId(newDevId);
            }
        };

        update.setOnClickListener(listener);

        /**
         * Start the server and advertise the service via mDNS.
         */
        new AsyncTask<String, Object, PingPongService>() {
            @Override
            protected PingPongService doInBackground(String... strings) {
                service = new PingPongService(null, FrontPage.this);
                return service;
            }
        }.execute();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        service.stop();
        service = null;
    }

    private void changeId(final String id) {
        findViewById(R.id.ok_button).setEnabled(false);
        new AsyncTask<String, String, Boolean>() {
            @Override
            protected Boolean doInBackground(String... strings) {
                return service.changeId(id);
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                findViewById(R.id.ok_button).setEnabled(true);
                super.onPostExecute(aBoolean);
            }
        }.execute(id);
    }
}

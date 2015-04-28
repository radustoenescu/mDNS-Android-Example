package org.pdsd.pingpong;

import android.app.Activity;
import android.database.DataSetObserver;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.pdsd.pingpong.network.AndroidWiFiTCPServer;
import org.pdsd.pingpong.service.AndroidDNSSetupHooks;
import org.pdsd.pingpong.service.NetworkService;

import javax.jmdns.ServiceInfo;
import java.net.UnknownServiceException;
import java.util.LinkedList;

public class FrontPage extends Activity {
    private static final String LOG_TAG = "Fr Page";
    private NetworkService service;

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
        new AsyncTask<String, Object, NetworkService>() {
            @Override
            protected NetworkService doInBackground(String... strings) {
                AndroidDNSSetupHooks hooks = new AndroidDNSSetupHooks(FrontPage.this);
                try {
                    return new NetworkService(null, AndroidWiFiTCPServer.build(FrontPage.this), hooks, hooks);
                } catch (UnknownServiceException e) {
                    Log.e(LOG_TAG, e.getMessage());
                    return null;
                }
            }

            @Override
            protected void onPostExecute(NetworkService networkService) {
                FrontPage.this.service = networkService;
                final ListView peerList = (ListView)findViewById(R.id.peer_list);
                final ArrayAdapter<String> adapter =
                        new ArrayAdapter<String>(FrontPage.this,
                                android.R.layout.simple_list_item_1,
                                new LinkedList<String>());
                peerList.setAdapter(adapter);
                service.setOnNewServiceCallback(new NetworkService.ServiceEventHandler() {
                    @Override
                    public void handle(ServiceInfo si) {
                        adapter.add(si.getName());
                        adapter.notifyDataSetChanged();
                    }
                });
                service.setOnServiceRemovedCallback(new NetworkService.ServiceEventHandler() {
                    @Override
                    public void handle(ServiceInfo si) {
                        adapter.remove(si.getName());
                        adapter.notifyDataSetChanged();
                    }
                });
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

// Copyright (c) 2010 Evan Martin.  All rights reserved.
// Use of this source code is governed by a BSD-style license that can
// be found in the LICENSE file.

package org.neugierig.proximo;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.util.Log;
import java.util.HashMap;

public class Stop extends Activity implements View.OnClickListener {
  private String mRouteId;
  private String mRouteName;
  private String mRunId;
  private String mRunName;
  private String mStopId;
  private String mStopName;
  private ProximoBus.Prediction[] mPredictions;

  private StarDBAdapter mStarDB;
  private CheckBox mStarView;

  private HashMap<String, String> mRouteNames = new HashMap<String, String>();
  private HashMap<String, String> mRunNames = new HashMap<String, String>();

  private AsyncBackend mBackend = new AsyncBackend(this);
  private IUpdateService mService;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.stop);

    mStarDB = new StarDBAdapter(this);

    Bundle extras = getIntent().getExtras();
    mRouteId = extras.getString(ViewState.ROUTE_ID_KEY);
    mRouteName = extras.getString(ViewState.ROUTE_NAME_KEY);
    mRunId = extras.getString(ViewState.RUN_ID_KEY);
    mRunName = extras.getString(ViewState.RUN_NAME_KEY);
    mStopId = extras.getString(ViewState.STOP_ID_KEY);
    mStopName = extras.getString(ViewState.STOP_NAME_KEY);

    // Put the names we already know in the lookup table to save us
    // a couple of lookups when we come to render the predictions list.
    mRouteNames.put(mRouteId, mRouteName);
    mRunNames.put(mRunId, mRunName);

    TextView title = (TextView) findViewById(R.id.title);
    title.setText(mStopName);

    mStarView = (CheckBox) findViewById(R.id.star);
    mStarView.setOnClickListener(this);
    mStarView.setChecked(mStarDB.isStopAFavorite(mRouteId, mStopId));

    // Turn on the in-progress throbber to show that we're continually fetching
    // new stop info.
    setProgressBarIndeterminateVisibility(true);
  }

  @Override
  public void onStart() {
    super.onStart();
    bindService(new Intent(this, UpdateService.class), mConnection,
                Context.BIND_AUTO_CREATE);
  }
  @Override
  public void onStop() {
    super.onStop();
    unbindService(mConnection);
  }

  private IUpdateMonitor.Stub mUpdateCallback = new IUpdateMonitor.Stub() {
      @Override
      public void onNewPredictions(ProximoBus.Prediction[] predictions) {
        mHandler.sendMessage(
            mHandler.obtainMessage(MSG_NEW_PREDICTIONS, predictions));
      }
    };

  private ServiceConnection mConnection = new ServiceConnection() {
      @Override
      public void onServiceConnected(ComponentName className,
                                     IBinder service) {
        mService = IUpdateService.Stub.asInterface(service);
        try {
          mService.monitorStop(mRouteId, mStopId, mUpdateCallback);
        } catch (RemoteException e) {
          // In this case the service has crashed before we could even
          // do anything with it; we can count on soon being
          // disconnected (and then reconnected if it can be
          // restarted) so there is no need to do anything here.
        }
      }

      @Override
      public void onServiceDisconnected(ComponentName className) {
        mService = null;
      }
    };

  private final static int MSG_NEW_PREDICTIONS = 0;
  private Handler mHandler = new Handler() {
      @Override public void handleMessage(Message msg) {
        switch (msg.what) {
          case MSG_NEW_PREDICTIONS:
            showPredictions((ProximoBus.Prediction[])msg.obj);
            break;
          default:
            super.handleMessage(msg);
        }
      }
    };

  public void showPredictions(ProximoBus.Prediction[] predictions) {
    mPredictions = predictions;

    ListView list = (ListView) findViewById(R.id.list);
    ListAdapter adapter;
    if (mPredictions.length > 0) {
      adapter = new PredictionsListAdapter(
          this,
          mPredictions);
    } else {
      adapter = new ArrayAdapter<String>(
          this,
          android.R.layout.simple_list_item_1,
          new String[] {"(no arrivals predicted)"});
    }
    list.setAdapter(adapter);
  }

  @Override
  public void onClick(View view) {
    switch (view.getId()) {
      case R.id.star:
        if (mStarView.isChecked())
          mStarDB.addStopAsFavorite(mRouteId, mRouteName, mRunId, mRunName, mStopId, mStopName);
        else
          mStarDB.removeStopAsFavorite(mRouteId, mStopId);
        break;
    }
  }

  private class RouteQuery implements AsyncBackend.Query {
    final String mRouteId;
    RouteQuery(String routeId) {
      mRouteId = routeId;
    }
    public Object runQuery(Backend backend) throws Exception {
      return backend.fetchRoute(mRouteId);
    }
  }
 
  private class RunQuery implements AsyncBackend.Query {
    final String mRouteId;
    final String mRunId;
    RunQuery(String routeId, String runId) {
      mRouteId = routeId;
      mRunId = runId;
    }
    public Object runQuery(Backend backend) throws Exception {
      return backend.fetchRun(mRouteId, mRunId);
    }
  }

  private class PredictionsListAdapter extends ArrayAdapter<ProximoBus.Prediction> {
    private RouteFetchCallback mRouteCallback;
    private RunFetchCallback mRunCallback;

    public PredictionsListAdapter(Stop context, ProximoBus.Prediction[] objects) {
      super(context, android.R.layout.simple_list_item_1, objects);
      this.mRouteCallback = new RouteFetchCallback();
      this.mRunCallback = new RunFetchCallback();
    }

    private class RouteFetchCallback implements AsyncBackend.APIResultCallback {
      public void onAPIResult(Object obj) {
        ProximoBus.Route route = (ProximoBus.Route) obj;
        Stop.this.mRouteNames.put(route.id, route.displayName);
        PredictionsListAdapter.this.notifyDataSetChanged();
      }
      public void onException(Exception exn) {
        // Not the end of the world if we don't get a pretty display name. Ignore.
      }
    }

    private class RunFetchCallback implements AsyncBackend.APIResultCallback {
      public void onAPIResult(Object obj) {
        ProximoBus.Run run = (ProximoBus.Run) obj;
        Stop.this.mRunNames.put(run.id, run.displayName);
        PredictionsListAdapter.this.notifyDataSetChanged();
      }
      public void onException(Exception exn) {
        // Not the end of the world if we don't get a pretty display name. Ignore.
      }
    }

    public View getView(int position, View convertView, ViewGroup parent) {
      View ret;

      if (convertView != null) {
        ret = convertView;
      }
      else {
        ret = View.inflate(this.getContext(), R.layout.prediction_item, null);
      }

      ProximoBus.Prediction prediction = getItem(position);

      String routeDisplayName;
      String runDisplayName;

      if (mRouteNames.containsKey(prediction.routeId)) {
        routeDisplayName = mRouteNames.get(prediction.routeId);
      }
      else {
        routeDisplayName = null;
        mBackend.startQuery(new RouteQuery(prediction.routeId), mRouteCallback);

        // Drop an explicit null in the dictionary so we don't fire off two queries for the same item.
        mRouteNames.put(prediction.routeId, null);
      }
      if (routeDisplayName == null) {
        // Use the routeId as a placeholder until our network fetch completes.
        routeDisplayName = prediction.routeId;
      }

      if (mRunNames.containsKey(prediction.runId)) {
        runDisplayName = mRunNames.get(prediction.runId);
      }
      else {
        runDisplayName = null;
        mBackend.startQuery(new RunQuery(prediction.routeId, prediction.runId), mRunCallback);

        // Drop an explicit null in the dictionary so we don't fire off two queries for the same item.
        mRunNames.put(prediction.runId, null);
      }
      if (runDisplayName == null) {
        // Just leave the run name blank until our network fetch completes.
        runDisplayName = "";
      }

      TextView routeNameView = (TextView) ret.findViewById(R.id.route_name);
      routeNameView.setText(routeDisplayName);

      TextView runNameView = (TextView) ret.findViewById(R.id.run_name);
      runNameView.setText(runDisplayName);
      
      TextView predictedTimeView = (TextView) ret.findViewById(R.id.predicted_time);
      predictedTimeView.setText(prediction.minutes+" min");
      

      return ret;
    }

  }



}

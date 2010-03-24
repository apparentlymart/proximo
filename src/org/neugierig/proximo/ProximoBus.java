// Copyright (c) 2010 Martin Atkins.  All rights reserved.
// Use of this source code is governed by a BSD-style license that can
// be found in the LICENSE file.

/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

package org.neugierig.proximo;

import android.os.Parcel;
import android.os.Parcelable;
import java.net.*;
import java.io.*;
import org.json.*;

class ProximoBus {
  private static final String API_URL = "http://proximobus.appspot.com/agencies/sf-muni/";

  public static class Displayable {
    public String displayName;

    public String toString() {
      return displayName;
    }
  }

  public static class Route extends Displayable {
    static final String RUNS_PATH_FORMAT = "routes/%s/runs.json";

    public String id;

    public static Route fromJsonObject(JSONObject obj) throws JSONException {
      Route route = new Route();
      route.displayName = obj.getString("display_name");
      route.id = obj.getString("id");
      return route;
    }
  }

  public static class Run extends Displayable {
    static final String STOPS_PATH_FORMAT = "routes/%s/runs/%s/stops.json";
    public String id;
    public String routeId;
    public boolean displayInUi;

    public static Run fromJsonObject(JSONObject obj) throws JSONException {
      Run run = new Run();
      run.displayName = obj.getString("display_name");
      run.id = obj.getString("id");
      run.routeId = obj.getString("route_id");
      run.displayInUi = obj.getBoolean("display_in_ui");
      return run;
    }
  }

  public static class Stop extends Displayable {
    static final String PREDICTIONS_PATH_FORMAT = "stops/%s/predictions.json";
    static final String PREDICTIONS_BY_ROUTE_PATH_FORMAT = "stops/%s/predictions/by-route/%s.json";

    public String id;

    public static Stop fromJsonObject(JSONObject obj) throws JSONException {
      Stop stop = new Stop();
      stop.displayName = obj.getString("display_name");
      stop.id = obj.getString("id");
      return stop;
    }
  }

  public static class Prediction implements Parcelable {
    public String routeId;
    public String runId;
    public int minutes;
    public boolean isDeparting;

    public static Prediction fromJsonObject(JSONObject obj) throws JSONException {
      Prediction prediction = new Prediction();
      prediction.routeId = obj.getString("route_id");
      prediction.runId = obj.getString("run_id");
      prediction.minutes = obj.getInt("minutes");
      prediction.isDeparting = obj.getBoolean("is_departing");
      return prediction;
    }

    public String toString() {
      if (minutes == 0) {
        if (isDeparting) {
          return "Departing";
        }
        else {
          return "Arriving";
        }
      }
      else {
        if (minutes == 1) {
          return "1 minute";
        }
        else {
          return minutes+" minutes";
        }
      }
    }

    public Prediction() {}

    public Prediction(Parcel in) {
      routeId = in.readString();
      runId = in.readString();
      minutes = in.readInt();
      isDeparting = in.readInt() != 0;
    }

    @Override
    public int describeContents() {
      return 0;  // No special Parcelable info.
    }
    @Override
    public void writeToParcel(Parcel out, int flags) {
      out.writeString(routeId);
      out.writeString(runId);
      out.writeInt(minutes);
      out.writeInt(isDeparting ? 1 : 0);
    }

    public static final Parcelable.Creator<Prediction> CREATOR =
        new Parcelable.Creator<Prediction>() {
      @Override
      public Prediction createFromParcel(Parcel in) {
        return new Prediction(in);
      }

      @Override
      public Prediction[] newArray(int size) {
        return new Prediction[size];
      }
    };
  }

  static String getAllRoutesPath() {
    return "routes.json";
  }

  static String getRunsOnRoutePath(String routeId) {
    // FIXME: URLEncode.encode turns " " into "+", but ProximoBus expects "%20".
    // this means that we can't currently support Owl routes, which have ids like "N OWL".
    return String.format(Route.RUNS_PATH_FORMAT, URLEncoder.encode(routeId));
  }

  static String getStopsOnRunPath(String routeId, String runId) {
    return String.format(Run.STOPS_PATH_FORMAT, URLEncoder.encode(routeId), runId);
  }

  static String getStopPredictionsByRoutePath(String stopId, String routeId) {
    return String.format(Stop.PREDICTIONS_BY_ROUTE_PATH_FORMAT, stopId, URLEncoder.encode(routeId));
  }

  static String getStopPredictionsPath(String stopId) {
    return String.format(Stop.PREDICTIONS_PATH_FORMAT, URLEncoder.encode(stopId));
  }

  static Route[] parseRoutes(String data) throws JSONException {
    JSONObject res = new JSONObject(data);
    JSONArray array = res.getJSONArray("items");
    Route[] routes = new Route[array.length()];
    for (int i = 0; i < array.length(); ++i) {
      JSONObject entry = array.getJSONObject(i);
      routes[i] = Route.fromJsonObject(entry);
    }
    return routes;
  }

  static Run[] parseRuns(String data) throws JSONException {
    JSONObject res = new JSONObject(data);
    JSONArray array = res.getJSONArray("items");
    // This assumes that there are only ever two displayInUi runs.
    // This seems to be true for all of the Muni routes I've looked at,
    // but if it turns out that some have three or more we'll need to
    // revisit this.
    Run[] runs = new Run[2];
    int runIndex = 0;
    for (int i = 0; i < array.length(); ++i) {
      JSONObject entry = array.getJSONObject(i);
      Run run = Run.fromJsonObject(entry);
      if (run.displayInUi) {
        runs[runIndex++] = Run.fromJsonObject(entry);
        if (runIndex > 1) break;
      }
    }
    return runs;
  }

  static Stop[] parseStops(String data) throws JSONException {
    JSONObject res = new JSONObject(data);
    JSONArray array = res.getJSONArray("items");
    Stop[] stops = new Stop[array.length()];
    for (int i = 0; i < array.length(); ++i) {
      JSONObject entry = array.getJSONObject(i);
      stops[i] = Stop.fromJsonObject(entry);
    }
    return stops;
  }

  static Prediction[] parsePredictions(String data) throws JSONException {
    JSONObject res = new JSONObject(data);
    JSONArray array = res.getJSONArray("items");
    Prediction[] predictions = new Prediction[array.length()];
    for (int i = 0; i < array.length(); ++i) {
      JSONObject entry = array.getJSONObject(i);
      predictions[i] = Prediction.fromJsonObject(entry);
    }
    return predictions;
  }

  static String queryNetwork(String path)
    throws MalformedURLException, IOException
  {
    return fetchURL(new URL(API_URL + path));
  }

  // It's pretty unbelievable there's no simpler way to do this.
  static String fetchURL(URL url) throws IOException {
    InputStream input = url.openStream();
    StringBuffer buffer = new StringBuffer(8 << 10);

    int byte_read;
    while ((byte_read = input.read()) != -1) {
      // This is incorrect for non-ASCII, but we don't have any of that.
      buffer.appendCodePoint(byte_read);
    }

    return buffer.toString();
  }

}

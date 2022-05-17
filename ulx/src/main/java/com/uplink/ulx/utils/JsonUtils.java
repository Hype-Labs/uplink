package com.uplink.ulx.utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Objects;

import timber.log.Timber;

public class JsonUtils {

    public static void minifyInternetRequestData(JSONObject jsonObject) {
        try {
            JSONObject filtered = removeEmptyJsonFields(jsonObject, Constants.getTransactionDataTemplate());
            Timber.d("Minified JSON : " + filtered.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static JSONObject removeEmptyJsonFields(JSONObject jsonObject, JSONObject template) {
        Iterator<String> keys = template.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try {
                JSONObject jsonObj = jsonObject.getJSONObject(key);
                JSONObject templateObj = template.getJSONObject(key);
                boolean hasObjectsInside = jsonObj.keys().hasNext();
                if (hasObjectsInside) {
                    removeEmptyJsonFields(jsonObj, templateObj);
                    if (jsonObj.length() == 0) {
                        keys.remove();
                        jsonObject.remove(key);
                    }
                }
            } catch (JSONException e) {
                // empty catch block, if the keys are found we don't want to change anything
            }

            Object object = null;
            try {
                object = jsonObject.get(key);
                if (object == null) {
                    keys.remove();
                    jsonObject.remove(key);
                }
                boolean propertyValuePresent = object != "null" && !object.toString().isEmpty();
                if (!propertyValuePresent) {
                    keys.remove();
                    jsonObject.remove(key);
                }
            } catch (JSONException e) {
                // empty catch block, if the keys are found we don't want to change anything
            }
        }
        return jsonObject;
    }

    public static String fillGapsInternetResponseData(String data) {
        JSONObject object;
        String result = null;
        try {
            object = new JSONObject(data);
            fillEmptyResponseFields(object, Constants.getTransactionResponseTemplate());
            Timber.d("Full-Response JSON : " + object);
            result = object.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Objects.requireNonNullElse(result, data);
    }

    private static JSONObject fillEmptyResponseFields(JSONObject jsonObject, JSONObject template) {
        Iterator<String> keys = template.keys();
        while (keys.hasNext()) {
            String key = keys.next();

            // if it is an object
            try {
                JSONObject templateObj = template.getJSONObject(key);
                boolean hasObjectsInsideTemplate = template.keys().hasNext();
                JSONObject jsonObj = null;
                try {
                    jsonObj = jsonObject.getJSONObject(key);
                } catch (JSONException e) {
                    // means the object doesnt exist
                    jsonObj = new JSONObject();
                }

                if (hasObjectsInsideTemplate) {
                    JSONObject filled = fillEmptyResponseFields(jsonObj, templateObj);
                    jsonObject.put(key, filled);
                }
            } catch (JSONException e) {
                // do nothing
            }

            // is it is just a String value
            Object templateObject;
            try {
                templateObject = template.get(key);
                if (!jsonObject.has(key)) {
                    jsonObject.put(key, templateObject);
                }
            } catch (JSONException e) {
                // do nothing
            }
        }
        return jsonObject;
    }
}

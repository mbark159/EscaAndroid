package me.esca.services.escaWS.recipes;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import me.esca.model.Recipe;

import static me.esca.services.escaWS.recipes.Utils.ADD_RECIPE_URL;
import static me.esca.services.escaWS.recipes.Utils.MAIN_DOMAIN_NAME;

/**
 * Created by Me on 26/06/2017.
 */

public class AddNewRecipeService extends Service {

    private final IBinder mBinder = new MyBinder();
    private URI resultLocation;
    private Recipe recipeToBeAdded;
    private String loggedUsername = "Houssam";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.recipeToBeAdded = (Recipe) intent.getSerializableExtra("recipeToBeAdded");
        new AddNewRecipe().execute();
        return Service.START_NOT_STICKY;
    }

    public URI getResultLocation() {
        return resultLocation;
    }

    public Recipe getRecipeToBeAdded() {
        return recipeToBeAdded;
    }

    public void setRecipeToBeAdded(Recipe recipeToBeAdded) {
        this.recipeToBeAdded = recipeToBeAdded;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        new AddNewRecipe().execute();
        return mBinder;
    }

    public class MyBinder extends Binder {
        public AddNewRecipeService getService() {
            return AddNewRecipeService.this;
        }
    }

    private class AddNewRecipe extends AsyncTask<Void, Void, String>{

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected String doInBackground(Void... params) {

            RestTemplate restTemplate = new RestTemplate();
            List<HttpMessageConverter<?>> list = new ArrayList<>();
            list.add(new MappingJackson2HttpMessageConverter());
            restTemplate.setMessageConverters(list);

            resultLocation = restTemplate.postForLocation(MAIN_DOMAIN_NAME+ADD_RECIPE_URL.replace("{username}",
                    loggedUsername), recipeToBeAdded, Recipe.class);
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            Intent intent = new Intent("ServiceIsDone");
            intent.putExtra("resultLocation", resultLocation.toString());
            sendBroadcast(intent);
            stopSelf();
        }
    }
}

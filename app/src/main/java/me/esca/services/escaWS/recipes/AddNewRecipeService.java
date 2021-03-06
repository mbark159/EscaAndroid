package me.esca.services.escaWS.recipes;

import android.app.Service;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.Nullable;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferObserver;
import com.amazonaws.mobileconnectors.s3.transferutility.TransferUtility;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.esca.dbRelated.contentProvider.RecipesContentProvider;
import me.esca.dbRelated.image.tableUtils.ImagesTableDefinition;
import me.esca.model.Image;
import me.esca.model.Recipe;
import me.esca.utils.Accessors;
import me.esca.utils.Connectivity;
import me.esca.utils.ImageProcessing.Utils;
import me.esca.utils.security.cryptography.Encryption;

import static me.esca.services.escaWS.Utils.ADD_IMAGE_URL;
import static me.esca.services.escaWS.Utils.ADD_RECIPE_URL;
import static me.esca.services.escaWS.Utils.MAIN_DOMAIN_NAME;

/**
 * Created by Me on 26/06/2017.
 */

public class AddNewRecipeService extends Service {

    private final IBinder mBinder = new MyBinder();
    private URI resultLocation;
    private Recipe recipeToBeAdded;
    private String loggedUsername = "Houssam";
    private String imageUrl;
    private Uri imageUri;
    private Image imageToBeAdded;
    private Image imageResponse;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.recipeToBeAdded = (Recipe) intent.getSerializableExtra("recipeToBeAdded");
        this.imageToBeAdded =  (Image) intent.getSerializableExtra("imageToBeAdded");
        this.imageUrl = intent.getStringExtra("recipeImageUrl");
        this.imageUri = Uri.parse(intent.getStringExtra("recipeImageUrl")) ;
        if(Connectivity.isNetworkAvailable(this)) {
            new AddNewRecipe().execute();
        }

        return Service.START_NOT_STICKY;
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

            try {
                resultLocation = restTemplate.postForLocation(MAIN_DOMAIN_NAME+ADD_RECIPE_URL.replace("{username}",
                        loggedUsername), recipeToBeAdded, Recipe.class);
            } catch (Exception e){
                return null;
            }


            Long id = Long.parseLong(resultLocation.getPath().substring(resultLocation.getPath().lastIndexOf("/") + 1));
            String imagePath = Utils.getPathFromUri(getApplicationContext(), imageUri);
            imageToBeAdded.setExtension(imagePath.substring(imagePath.lastIndexOf(".")));
            imageResponse = restTemplate.postForObject(MAIN_DOMAIN_NAME+ADD_IMAGE_URL.replace("{recipeId}",
                    String.valueOf(id)), imageToBeAdded, Image.class);

            ContentValues contentValues = new ContentValues();
            contentValues.put(ImagesTableDefinition.ID_COLUMN, imageResponse.getId());
            contentValues.put(ImagesTableDefinition.ORIGINAL_NAME_COLUMN, imageResponse.getOriginalName());
            contentValues.put(ImagesTableDefinition.ORIGINAL_PATH_COLUMN, imageResponse.getOriginalPath());
            contentValues.put(ImagesTableDefinition.DATE_CREATED_COLUMN, imageResponse.getDateCreated());
            contentValues.put(ImagesTableDefinition.LAST_UPDATED_COLUMN, imageResponse.getLastUpdated());
            contentValues.put(ImagesTableDefinition.IS_MAIN_PICTURE_COLUMN, imageResponse.isMainPicture());
            contentValues.put(ImagesTableDefinition.COOK_ID_COLUMN, "");
            contentValues.put(ImagesTableDefinition.RECIPE_ID_COLUMN, id);
            contentValues.put(ImagesTableDefinition.EXTENSION_COLUMN, imageResponse.getExtension());

            getApplicationContext().getContentResolver().insert(RecipesContentProvider.CONTENT_URI_IMAGES, contentValues);

            String pool = "";
            try {
                JSONArray jsonArray = new JSONArray(Accessors.loadJSONFromAsset(getApplicationContext()));
                JSONObject jsonObject = jsonArray.getJSONObject(0);
                pool = jsonObject.getString("syek3SSWA");
                pool = Encryption.decrypt(pool);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            CognitoCachingCredentialsProvider credentialsProvider = new CognitoCachingCredentialsProvider(
                    getApplicationContext(),
                    pool, // Identity pool ID
                    Regions.US_EAST_1 // Region
            );
            AmazonS3 s3 = new AmazonS3Client(credentialsProvider);

            ObjectMetadata myObjectMetadata = new ObjectMetadata();

            Map<String, String> userMetadata = new HashMap<String,String>();
            userMetadata.put("metadata","metadata");
            myObjectMetadata.setUserMetadata(userMetadata);
            File imageFile = new File(imagePath);
            TransferUtility transferUtility = new TransferUtility(s3, getApplicationContext());
            TransferObserver observer = transferUtility.upload(
                    "escaws",     /* The bucket to upload to */
                    "Image storage directory/" + String.valueOf(imageResponse.getId()
                            +imageResponse.getExtension()),
                    imageFile,        /* The file where the data to upload exists */
                    myObjectMetadata);

            return resultLocation.toString();
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            Intent intent = new Intent("ServiceIsDone");
            if(s != null){
                intent.putExtra("resultLocation", s);
            }else{
                intent.putExtra("resultLocation", "not added");
            }
            sendBroadcast(intent);
            stopSelf();
        }
    }
}

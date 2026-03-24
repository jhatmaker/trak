package com.trackmyraces.trak.data.network;

import android.content.Context;
import android.util.Log;

import com.trackmyraces.trak.BuildConfig;
import com.trackmyraces.trak.data.db.TrakDatabase;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;

/**
 * ApiClient — builds and caches the Retrofit TrakApiService.
 *
 * Auth: JWT token read from RunnerProfileDao and injected via AuthInterceptor.
 * Timeouts: connect 10s, read/write 90s (extraction calls can be slow).
 * Logging: full body logging in debug builds only.
 */
public class ApiClient {

    private static final String TAG = "ApiClient";

    private final TrakApiService mService;

    public ApiClient(Context context) {
        TrakDatabase db = TrakDatabase.getInstance(context);

        // Logging interceptor — debug builds only
        HttpLoggingInterceptor logger = new HttpLoggingInterceptor(message ->
            Log.d(TAG, message)
        );
        logger.setLevel(BuildConfig.DEBUG
            ? HttpLoggingInterceptor.Level.BODY
            : HttpLoggingInterceptor.Level.NONE
        );

        OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)   // extraction can take 15-20s
            .writeTimeout(30, TimeUnit.SECONDS)
            // Auth interceptor — adds Authorization header to every request
            .addInterceptor(chain -> {
                String token = db.runnerProfileDao().getAuthToken();
                Request original = chain.request();
                if (token != null && !token.isEmpty()) {
                    Request authenticated = original.newBuilder()
                        .header("Authorization", "Bearer " + token)
                        .header("X-App-Version", BuildConfig.APP_VERSION)
                        .build();
                    return chain.proceed(authenticated);
                }
                return chain.proceed(original);
            })
            .addInterceptor(logger)
            .build();

        Retrofit retrofit = new Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL + "/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build();

        mService = retrofit.create(TrakApiService.class);
        Log.d(TAG, "ApiClient initialised — base URL: " + BuildConfig.API_BASE_URL);
    }

    public TrakApiService getService() {
        return mService;
    }
}

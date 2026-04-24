package com.handleit.transit.app

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // TransitDb is provided via @Inject constructor in its own class, 
    // so we don't need a @Provides method here anymore.

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
            .build()

    @Provides @Singleton
    fun provideFusedLocation(
        @ApplicationContext ctx: Context,
    ): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(ctx)

    @Provides @Singleton
    fun provideGeofencingClient(
        @ApplicationContext ctx: Context,
    ): GeofencingClient =
        LocationServices.getGeofencingClient(ctx)
}

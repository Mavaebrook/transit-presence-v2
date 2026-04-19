package com.handleit.transit.app

import android.content.Context
import androidx.room.Room
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.LocationServices
import com.handleit.transit.data.gtfs.RouteDao
import com.handleit.transit.data.gtfs.ShapeDao
import com.handleit.transit.data.gtfs.StopDao
import com.handleit.transit.data.gtfs.StopTimeDao
import com.handleit.transit.data.gtfs.TransitDatabase
import com.handleit.transit.data.gtfs.TripDao
import com.handleit.transit.data.gtfsrt.GtfsRtClient
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

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): TransitDatabase =
        Room.databaseBuilder(ctx, TransitDatabase::class.java, "transit.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideStopDao(db: TransitDatabase): StopDao         = db.stopDao()
    @Provides fun provideRouteDao(db: TransitDatabase): RouteDao       = db.routeDao()
    @Provides fun provideTripDao(db: TransitDatabase): TripDao         = db.tripDao()
    @Provides fun provideStopTimeDao(db: TransitDatabase): StopTimeDao = db.stopTimeDao()
    @Provides fun provideShapeDao(db: TransitDatabase): ShapeDao       = db.shapeDao()

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

    @Provides @Singleton
    fun provideFusedLocation(@ApplicationContext ctx: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(ctx)

    @Provides @Singleton
    fun provideGeofencingClient(@ApplicationContext ctx: Context): GeofencingClient =
        LocationServices.getGeofencingClient(ctx)
}

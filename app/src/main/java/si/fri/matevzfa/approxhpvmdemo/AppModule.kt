package si.fri.matevzfa.approxhpvmdemo

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import si.fri.matevzfa.approxhpvmdemo.data.AppDatabase
import si.fri.matevzfa.approxhpvmdemo.har.ApproxHPVMWrapper
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideClassificationDb(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java, "classification-log"
    ).allowMainThreadQueries()
        .fallbackToDestructiveMigration()
        .build()

    @Singleton
    @Provides
    fun provideClassificationDao(
        db: AppDatabase
    ) = db.classificationDao()

    @Singleton
    @Provides
    fun provideTraceClassificationDao(
        db: AppDatabase
    ) = db.traceClassificationDao()

    @Singleton
    @Provides
    fun provideHpvm(@ApplicationContext context: Context): ApproxHPVMWrapper =
        ApproxHPVMWrapper(context).apply {
            init()
        }
}

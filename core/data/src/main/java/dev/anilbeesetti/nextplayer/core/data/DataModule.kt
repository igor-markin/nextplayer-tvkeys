package dev.anilbeesetti.nextplayer.core.data

import dev.anilbeesetti.nextplayer.core.database.DatabaseModule
import dev.anilbeesetti.nextplayer.core.datastore.di.DataStoreModule
import dev.anilbeesetti.nextplayer.core.media.MediaModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [DatabaseModule::class, DataStoreModule::class, MediaModule::class])
@ComponentScan
class DataModule

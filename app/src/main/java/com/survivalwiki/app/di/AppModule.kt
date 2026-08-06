package com.survivalwiki.app.di

import android.content.Context
import androidx.room.Room
import com.survivalwiki.app.data.AssetLoaders
import com.survivalwiki.app.data.Synonyms
import com.survivalwiki.core.data.corpus.CorpusInstaller
import com.survivalwiki.core.data.corpus.SqliteCorpusReader
import com.survivalwiki.core.data.saved.AppDatabase
import com.survivalwiki.core.data.saved.SavedAnswerDao
import com.survivalwiki.core.embedding.SpmUnigramTokenizer
import com.survivalwiki.core.llm.LlamaCppEngine
import com.survivalwiki.core.llm.LlmEngine
import com.survivalwiki.core.retrieval.CorpusReader
import com.survivalwiki.core.retrieval.PromptBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCorpusReader(@ApplicationContext context: Context): CorpusReader {
        val dbPath = CorpusInstaller.ensureInstalled(context)
        return SqliteCorpusReader(dbPath)
    }

    @Provides
    @Singleton
    fun provideTokenizer(@ApplicationContext context: Context): SpmUnigramTokenizer =
        AssetLoaders.loadTokenizer(context)

    @Provides
    @Singleton
    fun provideSynonyms(@ApplicationContext context: Context): Synonyms =
        AssetLoaders.loadSynonyms(context)

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

    @Provides
    fun provideSavedAnswerDao(db: AppDatabase): SavedAnswerDao = db.savedAnswerDao()

    @Provides
    @Singleton
    fun providePromptBuilder(@ApplicationContext context: Context): PromptBuilder =
        PromptBuilder(AssetLoaders.loadGroundedPrompt(context))

    // Engine LLM unico. load() fallisce con grazia se la libreria nativa non è compilata
    // (build senza -PwithLlama): l'app resta in modalità "solo estratti".
    @Provides
    @Singleton
    fun provideLlmEngine(): LlmEngine = LlamaCppEngine()
}

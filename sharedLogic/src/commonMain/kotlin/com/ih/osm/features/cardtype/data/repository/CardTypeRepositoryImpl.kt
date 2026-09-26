package com.ih.osm.features.cardtype.data.repository

import com.ih.osm.core.network.NetworkResult
import com.ih.osm.database.AppDatabase
import com.ih.osm.features.cardtype.domain.model.CardType
import com.ih.osm.features.cardtype.domain.repository.CardTypeRepository
import com.ih.osm.features.catalog.data.remote.CatalogApiService
import com.ih.osm.features.catalog.data.remote.mapSuccess

internal class CardTypeRepositoryImpl(
    private val api: CatalogApiService,
    private val database: AppDatabase,
) : CardTypeRepository {
    override suspend fun fetchRemote(siteId: Long): NetworkResult<List<CardType>> =
        api.getCardTypes(siteId).mapSuccess { items -> items.map { it.toDomain() } }

    override fun getAll(siteId: Long): List<CardType> =
        database.catalogsQueries.selectCardTypesBySite(siteId).executeAsList().map {
            CardType(
                id = it.id,
                methodology = it.methodology,
                name = it.name,
                description = it.description,
                color = it.color,
                owner = it.owner,
                status = it.status,
                quantityImagesCreate = it.quantity_images_create,
                quantityAudiosCreate = it.quantity_audios_create,
                quantityVideosCreate = it.quantity_videos_create,
                audiosDurationCreate = it.audios_duration_create,
                videosDurationCreate = it.videos_duration_create,
                quantityImagesClose = it.quantity_images_close,
                quantityAudiosClose = it.quantity_audios_close,
                quantityVideosClose = it.quantity_videos_close,
                audiosDurationClose = it.audios_duration_close,
                videosDurationClose = it.videos_duration_close,
                quantityImagesPs = it.quantity_images_ps,
                quantityAudiosPs = it.quantity_audios_ps,
                quantityVideosPs = it.quantity_videos_ps,
                audiosDurationPs = it.audios_duration_ps,
                videosDurationPs = it.videos_duration_ps,
                cardTypeMethodology = it.card_type_methodology,
            )
        }

    override fun count(siteId: Long): Long =
        database.catalogsQueries.countCardTypesBySite(siteId).executeAsOne()

    override fun replaceAll(siteId: Long, items: List<CardType>) {
        database.catalogsQueries.deleteCardTypesBySite(siteId)
        items.forEach { item ->
            database.catalogsQueries.insertCardType(
                siteId, item.id, item.methodology, item.name, item.description, item.color,
                item.owner, item.status, item.quantityImagesCreate, item.quantityAudiosCreate,
                item.quantityVideosCreate, item.audiosDurationCreate, item.videosDurationCreate,
                item.quantityImagesClose, item.quantityAudiosClose, item.quantityVideosClose,
                item.audiosDurationClose, item.videosDurationClose, item.quantityImagesPs,
                item.quantityAudiosPs, item.quantityVideosPs, item.audiosDurationPs,
                item.videosDurationPs, item.cardTypeMethodology,
            )
        }
    }

    override fun deleteAll() {
        database.catalogsQueries.deleteAllCardTypes()
    }
}

package com.rarible.protocol.union.enrichment.repository.search.internal

import com.rarible.protocol.union.core.model.EsActivity
import com.rarible.protocol.union.core.model.EsActivitySort
import com.rarible.protocol.union.core.model.ElasticActivityFilter
import com.rarible.protocol.union.core.model.ElasticActivityQueryGenericFilter
import com.rarible.protocol.union.core.model.ElasticActivityQueryPerTypeFilter
import com.rarible.protocol.union.core.model.cursor
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.MatchQueryBuilder
import org.elasticsearch.index.query.RangeQueryBuilder
import org.elasticsearch.index.query.TermsQueryBuilder
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder
import org.springframework.stereotype.Component

@Component
class EsQueryBuilderService(
    private val sortService: EsQuerySortService,
    private val cursorService: EsQueryCursorService,
) {

    companion object {
        private val userMaker = EsActivity::user.name + "." + EsActivity.User::maker.name
        private val userTaker = EsActivity::user.name + "." + EsActivity.User::taker.name
        private val collectionMake = EsActivity::collection.name + "." + EsActivity.Collection::make.name
        private val collectionTake = EsActivity::collection.name + "." + EsActivity.Collection::take.name
        private val itemMake = EsActivity::item.name + "." + EsActivity.Item::make.name
        private val itemTake = EsActivity::item.name + "." + EsActivity.Item::take.name
    }

    fun build(filter: ElasticActivityFilter, sort: EsActivitySort): NativeSearchQuery {
        val builder = NativeSearchQueryBuilder()
        val query = BoolQueryBuilder()
        when (filter) {
            is ElasticActivityQueryGenericFilter -> query.applyGenericFilter(filter)
            is ElasticActivityQueryPerTypeFilter -> query.applyPerTypeFilter(filter)
        }
        sortService.applySort(builder, sort)
        cursorService.applyCursor(query, sort, filter.cursor)

        builder.withQuery(query)
        return builder.build()
    }

    private fun BoolQueryBuilder.applyGenericFilter(filter: ElasticActivityQueryGenericFilter) {
        mustMatchTerms(filter.blockchains, EsActivity::blockchain.name)
        mustMatchTerms(filter.activityTypes, EsActivity::type.name)
        anyMustMatchTerms(filter.anyUsers, userMaker, userTaker)
        mustMatchTerms(filter.makers, userMaker)
        mustMatchTerms(filter.takers, userTaker)
        anyMustMatchTerms(filter.anyCollections, collectionMake, collectionTake)
        mustMatchTerms(filter.makeCollections, collectionMake)
        mustMatchTerms(filter.takeCollections, collectionTake)
        anyMustMatchKeyword(filter.anyItem, itemMake, itemTake)
        mustMatchKeyword(filter.makeItem, itemMake)
        mustMatchKeyword(filter.takeItem, itemTake)

        if (filter.from != null || filter.to != null) {
            val rangeQueryBuilder = RangeQueryBuilder(EsActivity::date.name)
            if (filter.from != null) {
                rangeQueryBuilder.gte(filter.from)
            }
            if (filter.to != null) {
                rangeQueryBuilder.lte(filter.to)
            }
            must(rangeQueryBuilder)
        }
    }

    private fun BoolQueryBuilder.applyPerTypeFilter(filter: ElasticActivityQueryPerTypeFilter) {
        TODO("To be implemented under ALPHA-276 Epic")
    }

    private fun BoolQueryBuilder.mustMatchTerms(terms: Set<*>, field: String) {
        if (terms.isNotEmpty()) {
            must(TermsQueryBuilder(field, prepareTerms(terms)))
        }
    }

    private fun BoolQueryBuilder.mustMatchKeyword(keyword: String?, field: String) {
        if (!keyword.isNullOrEmpty()) {
            must(MatchQueryBuilder(field, keyword))
        }
    }

    private fun BoolQueryBuilder.anyMustMatchTerms(terms: Set<*>, vararg fields: String) {
        if (terms.isNotEmpty()) {
            val boolQueryBuilder = BoolQueryBuilder()
            val preparedTerms = prepareTerms(terms)
            fields.forEach {
                boolQueryBuilder.should(TermsQueryBuilder(it, preparedTerms))
            }
            boolQueryBuilder.minimumShouldMatch(1)
            must(boolQueryBuilder)
        }
    }

    private fun BoolQueryBuilder.anyMustMatchKeyword(keyword: String?, vararg fields: String) {
        if (!keyword.isNullOrEmpty()) {
            val boolQueryBuilder = BoolQueryBuilder()
            fields.forEach {
                boolQueryBuilder.should(MatchQueryBuilder(it, keyword))
            }
            boolQueryBuilder.minimumShouldMatch(1)
            must(boolQueryBuilder)
        }
    }

    private fun prepareTerms(terms: Set<*>): List<String> {
        return terms.map { it.toString().lowercase() }
    }
}

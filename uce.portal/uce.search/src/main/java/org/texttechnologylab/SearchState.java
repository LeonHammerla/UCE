package org.texttechnologylab;

import org.apache.http.annotation.Obsolete;
import org.joda.time.DateTime;
import org.texttechnologylab.config.CorpusConfig;
import org.texttechnologylab.models.dto.UceMetadataFilterDto;
import org.texttechnologylab.states.KeywordInContextState;
import org.texttechnologylab.models.corpus.Document;
import org.texttechnologylab.models.search.*;

import java.util.*;

/**
 * A class that holds all states of a biofid search. We can use this class to serialize the search. It shouldn't hold any services.
 */
public class SearchState {
    private UUID searchId;
    private DateTime created;
    private boolean cleanupNextCycle;

    /**
     * The raw search phrase
     */
    private String searchPhrase;
    private List<String> searchTokens;
    private List<SearchLayer> searchLayers;
    private List<UceMetadataFilterDto> uceMetadataFilters;
    private SearchType searchType;
    private Integer currentPage = 1;
    private Integer take = 10;
    private long corpusId;
    private CorpusConfig corpusConfig;
    private Integer totalHits;
    private SearchOrder order = SearchOrder.ASC;
    private OrderByColumn orderBy = OrderByColumn.DOCUMENTTITLE;
    private ArrayList<AnnotationSearchResult> foundNamedEntities;
    private ArrayList<AnnotationSearchResult> foundTimes;
    private ArrayList<AnnotationSearchResult> foundTaxons;
    private KeywordInContextState keywordInContextState;

    /**
     * This is only filled when the search layer contains embeddings
     */
    private ArrayList<DocumentChunkEmbeddingSearchResult> foundDocumentChunkEmbeddings;

    private String primarySearchLayer;

    /**
     * These are the current, paginated list of documents
     */
    private List<Document> currentDocuments;

    /**
     * This is currently not used.
     */
    @Obsolete
    private List<Integer> currentDocumentHits;
    private HashMap<Integer, String> documentIdxToSnippet;
    private HashMap<Integer, Float> documentIdxToRank;

    public SearchState(SearchType searchType) {
        this.searchType = searchType;
        this.searchId = UUID.randomUUID();
        this.created = DateTime.now();
    }

    public String getSearchPhrase() {
        return searchPhrase;
    }

    public float getPossibleRankOfDocumentIdx(Integer idx) {
        if (this.documentIdxToRank != null && this.documentIdxToRank.containsKey(idx))
            return this.documentIdxToRank.get(idx);
        return -1;
    }

    public void setDocumentIdxToRank(HashMap<Integer, Float> documentIdxToRank) {
        this.documentIdxToRank = documentIdxToRank;
    }

    public List<UceMetadataFilterDto> getUceMetadataFilters() {
        return uceMetadataFilters;
    }

    public void setUceMetadataFilters(List<UceMetadataFilterDto> uceMetadataFilters) {
        this.uceMetadataFilters = uceMetadataFilters;
    }

    public boolean isCleanupNextCycle() {
        return cleanupNextCycle;
    }

    public void setCleanupNextCycle(boolean cleanupNextCycle) {
        this.cleanupNextCycle = cleanupNextCycle;
    }

    public String getPossibleSnippetOfDocumentIdx(Integer idx) {
        if (this.documentIdxToSnippet != null && this.documentIdxToSnippet.containsKey(idx))
            return this.documentIdxToSnippet.get(idx);
        return null;
    }

    public DateTime getCreated() {
        return this.created;
    }

    public void setDocumentIdxToSnippet(HashMap<Integer, String> map) {
        this.documentIdxToSnippet = map;
    }

    public List<Integer> getCurrentDocumentHits() {
        return currentDocumentHits;
    }

    public void setCurrentDocumentHits(List<Integer> currentDocumentHits) {
        this.currentDocumentHits = currentDocumentHits;
    }

    public CorpusConfig getCorpusConfig() {
        return corpusConfig;
    }

    public void setCorpusConfig(CorpusConfig corpusConfig) {
        this.corpusConfig = corpusConfig;
    }

    public KeywordInContextState getKeywordInContextState() {
        return keywordInContextState;
    }

    public void setKeywordInContextState(KeywordInContextState keywordInContextState) {
        this.keywordInContextState = keywordInContextState;
    }

    public void setPrimarySearchLayer(String primarySearchLayer) {
        this.primarySearchLayer = primarySearchLayer;
    }

    public SearchType getSearchType() {
        return searchType;
    }

    public void setSearchType(SearchType searchType) {
        this.searchType = searchType;
    }

    public ArrayList<AnnotationSearchResult> getFoundTimes() {
        return foundTimes;
    }

    public ArrayList<AnnotationSearchResult> getFoundTaxons() {
        return foundTaxons;
    }

    public ArrayList<DocumentChunkEmbeddingSearchResult> getFoundDocumentChunkEmbeddings() {
        return foundDocumentChunkEmbeddings;
    }

    public void setFoundDocumentChunkEmbeddings(ArrayList<DocumentChunkEmbeddingSearchResult> foundDocumentChunkEmbeddings) {
        this.foundDocumentChunkEmbeddings = foundDocumentChunkEmbeddings;
    }

    public long getCorpusId() {
        return corpusId;
    }

    public void setCorpusId(long corpusId) {
        this.corpusId = corpusId;
    }

    public SearchOrder getOrder() {
        return order;
    }

    public void setOrder(SearchOrder order) {
        this.order = order;
    }

    public OrderByColumn getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(OrderByColumn orderBy) {
        this.orderBy = orderBy;
    }

    public Integer getTotalPages() {
        if (totalHits < take) return 1;
        return (int) Math.ceil((double) totalHits / take);
    }

    public Integer getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(Integer totalHits) {
        this.totalHits = totalHits;
    }

    public int getSearchHitsOfDocument(int documentId) {
        try {
            var documentIdx = currentDocuments.indexOf(currentDocuments.stream().filter(d -> d.getId() == documentId).findFirst().get());
            return currentDocumentHits.get(documentIdx);
        } catch (Exception ex) {
            // This exception should never happen!
            return -1;
        }
    }

    /**
     * Returns the anootation type (NamedEntities, Taxons, Times etc.) of the given document
     */
    public List<AnnotationSearchResult> getAnnotationsByTypeAndDocumentId(String annotationType, Integer documentId, String neType) {
        List<AnnotationSearchResult> currentAnnotations = new ArrayList<>();
        switch (annotationType) {
            case "NamedEntities":
                currentAnnotations = getNamedEntitiesByType(neType, 0, 9999999);
                break;
            case "Taxons":
                currentAnnotations = foundTaxons;
                break;
            case "Times":
                currentAnnotations = foundTimes;
                break;
        }
        currentAnnotations = currentAnnotations.stream().filter(a -> a.getDocumentId() == documentId).toList();
        currentAnnotations = currentAnnotations.stream().sorted(Comparator.comparingInt(AnnotationSearchResult::getOccurrences).reversed()).toList();
        return currentAnnotations;
    }

    public List<AnnotationSearchResult> getNamedEntitiesByType(String type, int skip, int take) {
        return foundNamedEntities.stream().filter(ne -> ne.getInfo().equals(type)).skip(skip).limit(take).toList();
    }

    public ArrayList<AnnotationSearchResult> getFoundNamedEntities() {
        return foundNamedEntities;
    }

    public void setFoundNamedEntities(ArrayList<AnnotationSearchResult> foundNamedEntities) {
        // We have so much wrong annotations like . or a - dont show those which are shorter than 2 characters.
        this.foundNamedEntities = new ArrayList<>(foundNamedEntities.stream().filter(e -> e.getCoveredText().length() > 2).sorted(Comparator.comparingInt(AnnotationSearchResult::getOccurrences).reversed()).toList());
    }

    public ArrayList<AnnotationSearchResult> getFoundTimes(int skip, int take) {
        return new ArrayList<>(foundTimes.stream().skip(skip).limit(take).toList());
    }

    public void setFoundTimes(ArrayList<AnnotationSearchResult> foundTimes) {
        this.foundTimes = new ArrayList<>(foundTimes.stream().filter(e -> e.getCoveredText().length() > 2).sorted(Comparator.comparingInt(AnnotationSearchResult::getOccurrences).reversed()).toList());
    }

    public ArrayList<AnnotationSearchResult> getFoundTaxons(int skip, int take) {
        return new ArrayList<>(foundTaxons.stream().skip(skip).limit(take).toList());
    }

    public void setFoundTaxons(ArrayList<AnnotationSearchResult> foundTaxons) {
        this.foundTaxons = new ArrayList<>(foundTaxons.stream().filter(e -> e.getCoveredText().length() > 2).sorted(Comparator.comparingInt(AnnotationSearchResult::getOccurrences).reversed()).toList());
    }

    public void setCurrentDocuments(List<Document> currentDocuments) {
        this.currentDocuments = currentDocuments;
        if (searchLayers != null && searchLayers.contains(SearchLayer.KEYWORDINCONTEXT)) {
            // Whenever we set new current documents, recalculate the context state
            if (keywordInContextState == null) keywordInContextState = new KeywordInContextState();
            keywordInContextState.recalculate(this.currentDocuments, this.searchTokens);
        }
    }

    public List<Document> getCurrentDocuments() {
        return currentDocuments;
    }

    public UUID getSearchId() {
        return searchId;
    }

    public void setSearchId(UUID searchId) {
        this.searchId = searchId;
    }

    public String getOriginalSearchQuery() {
        return searchPhrase;
    }

    public void setSearchPhrase(String searchPhrase) {
        this.searchPhrase = searchPhrase;
    }

    public List<String> getSearchTokens() {
        return searchTokens;
    }

    public String getSearchTokensAsString() {
        if (this.searchTokens == null) return "";
        return String.join(" ", this.searchTokens.stream().map(s -> "[" + s + "]").toList());
    }

    public void setSearchTokens(List<String> searchTokens) {
        this.searchTokens = searchTokens;
    }

    public List<SearchLayer> getSearchLayers() {
        return searchLayers;
    }

    public void setSearchLayers(List<SearchLayer> searchLayers) {
        this.searchLayers = searchLayers;
        if (searchLayers.contains(SearchLayer.FULLTEXT)) primarySearchLayer = "Fulltext";
        else primarySearchLayer = "Named-Entities";
    }

    /**
     * TODO: This needs rework. Hardcoded names and the whole search layers are awkward. They have ben redesigned
     * too many times now.
     *
     * @return
     */
    public String getPrimarySearchLayer() {
        return this.primarySearchLayer == null ? "Semantic Roles" : this.primarySearchLayer;
    }

    public Integer getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(Integer currentPage) {
        this.currentPage = currentPage;
    }

    public Integer getTake() {
        return take;
    }

    public void setTake(Integer take) {
        this.take = take;
    }
}

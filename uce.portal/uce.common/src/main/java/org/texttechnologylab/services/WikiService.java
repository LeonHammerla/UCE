package org.texttechnologylab.services;

import org.apache.jena.Jena;
import org.texttechnologylab.config.CommonConfig;
import org.texttechnologylab.exceptions.DatabaseOperationException;
import org.texttechnologylab.models.corpus.DocumentTopicDistribution;
import org.texttechnologylab.models.corpus.PageTopicDistribution;
import org.texttechnologylab.models.corpus.TopicDistribution;
import org.texttechnologylab.models.viewModels.wiki.AnnotationWikiPageViewModel;
import org.texttechnologylab.models.viewModels.wiki.TopicAnnotationWikiPageViewModel;
import org.texttechnologylab.states.KeywordInContextState;

import javax.print.Doc;
import java.sql.Array;
import java.sql.Connection;
import java.util.List;

public class WikiService {
    private PostgresqlDataInterface_Impl db = null;
    private RAGService ragService = null;
    private JenaSparqlService sparqlService = null;

    public WikiService(PostgresqlDataInterface_Impl db, RAGService ragService, JenaSparqlService sparqlService) {
        this.db = db;
        this.sparqlService = sparqlService;
        ragService = ragService;
    }

    /**
     * Gets a TopicAnnotationWikiPageViewModel that can be used to render a Wikipage for a Topic annotation.
     */
    public TopicAnnotationWikiPageViewModel buildTopicAnnotationWikiPageViewModel(long id, String type, String coveredText) throws DatabaseOperationException {
        var viewModel = new TopicAnnotationWikiPageViewModel();
        viewModel.setCoveredText(coveredText);
        Class<? extends TopicDistribution> clazz = null;

        // We have currently document level topics and page level topics.
        if (type.equals("TD")) {
            clazz = DocumentTopicDistribution.class;
            var docDist = db.getTopicDistributionById(DocumentTopicDistribution.class, id);
            viewModel.setTopicDistribution(docDist);
            viewModel.setDocument(docDist.getDocument());
            viewModel.setAnnotationType("Document Keyword");
        } else if (type.equals("TP")) {
            clazz = PageTopicDistribution.class;
            var pageDist = db.getTopicDistributionById(PageTopicDistribution.class, id);
            viewModel.setTopicDistribution(pageDist);
            viewModel.setPage(pageDist.getPage());
            viewModel.setDocument(db.getDocumentById(pageDist.getPage().getDocumentId()));
            viewModel.setAnnotationType("Page Keyword");
        }

        // Search for similar topic annotations, get them and visualize those.
        viewModel.setSimilarTopicDistributions(db.getTopicDistributionsByString(clazz, coveredText, 10)
                .stream()
                .filter(d -> d.getId() != id)
                .toList());
        viewModel.setCorpus(db.getCorpusById(viewModel.getDocument().getCorpusId()).getViewModel());

        var kwicState = new KeywordInContextState();
        kwicState.recalculate(List.of(viewModel.getDocument()), List.of(viewModel.getCoveredText()));
        viewModel.setKwicState(kwicState);

        return viewModel;
    }
}

package org.texttechnologylab;

import freemarker.template.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.texttechnologylab.config.CommonConfig;
import org.texttechnologylab.config.SpringConfig;
import org.texttechnologylab.exceptions.ExceptionUtils;
import org.texttechnologylab.freeMarker.RequestContextHolder;
import org.texttechnologylab.models.corpus.Corpus;
import org.texttechnologylab.models.corpus.UCELog;
import org.texttechnologylab.routes.CorpusUniverseApi;
import org.texttechnologylab.routes.DocumentApi;
import org.texttechnologylab.routes.RAGApi;
import org.texttechnologylab.routes.SearchApi;
import org.texttechnologylab.services.PostgresqlDataInterface_Impl;
import org.texttechnologylab.utils.SystemStatus;
import spark.ExceptionHandler;
import spark.ModelAndView;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.UUID;

import static spark.Spark.*;

/**
 * Hello world!
 */
public class App {
    private static final Configuration configuration = Configuration.getDefaultConfiguration();
    private static final Logger logger = LogManager.getLogger();
    private static CommonConfig commonConfig = null;

    public static void main(String[] args) throws IOException {

        logger.info("Starting the UCE web service...");

        // Application context for services
        var context = new AnnotationConfigApplicationContext(SpringConfig.class);
        logger.info("Loaded application context and services.");

        // Load in and test the language translation objects to handle multiple languages
        logger.info("Testing the language resources:");
        var languageResource = new LanguageResources("de-DE");
        logger.info(languageResource.get("search"));

        commonConfig = new CommonConfig();
        logger.info("Loaded the common config.");

        SessionManager.InitSessionManager(commonConfig.getSessionJobCleanupInterval());
        logger.info("Initialized the Session Manager.");

        // Set the folder for our template files of freemaker
        try {
            configuration.setDirectoryForTemplateLoading(new File(commonConfig.getTemplatesLocation()));

            // We use the externalLocation method so that the files in the public folder are hot reloaded
            staticFiles.externalLocation(commonConfig.getPublicLocation());
            logger.info("Setup FreeMarker templates and public folders.");
        } catch (Exception e) {
            logger.error("Error setting up FreeMarker, the application will hence shutdown.", e);
            return;
        }

        // Start the routes.
        logger.info("Initializing all the spark routes...");
        ExceptionUtils.tryCatchLog(() -> initSparkRoutes(context),
                (ex) -> logger.error("There was a problem initializing the spark routes, web service will be shut down.", ex));
        logger.info("Routes initialized - UCE web service has started!");
    }

    private static void initSparkRoutes(ApplicationContext context) {

        var searchApi = new SearchApi(context, configuration);
        var documentApi = new DocumentApi(context, configuration);
        var ragApi = new RAGApi(context, configuration);
        var corpusUniverseApi = new CorpusUniverseApi(context, configuration);

        before((request, response) -> {
            // Setup and log all API calls with some information.
            request.attribute("id", UUID.randomUUID().toString());
            logger.info("Received API call: ID={}, IP={}, Method={}, URI={}, BODY={}",
                    request.attribute("id"), request.ip(), request.requestMethod(), request.uri(), request.body());

            // Should we log to db as well?
            if(commonConfig.getLogToDb() && SystemStatus.PostgresqlDbStatus.isAlive()){
                var uceLog = new UCELog(request.ip(), request.requestMethod(), request.uri(), request.body());
                ExceptionUtils.tryCatchLog(
                        () -> context.getBean(PostgresqlDataInterface_Impl.class).saveUceLog(uceLog),
                        (ex) -> logger.error("Error storing a log to the database: ", ex));
                logger.info("Last log was also logged to the db with id " + uceLog.getId());
            }

            // Check if the request contains a language parameter
            var languageResources = LanguageResources.fromRequest(request);
            response.header("Content-Language", languageResources.getDefaultLanguage());
            RequestContextHolder.setLanguageResources(languageResources);
        });

        // Landing page
        get("/", (request, response) -> {
            var model = new HashMap<String, Object>();
            model.put("title", "Unified Corpus Explorer");
            model.put("corpora", context.getBean(PostgresqlDataInterface_Impl.class)
                    .getAllCorpora()
                    .stream().map(Corpus::getViewModel)
                    .toList());
            var sparqlStatus = SystemStatus.JenaSparqlStatus.isAlive();
            model.put("isSparqlAlive", sparqlStatus);

            // The vm files are located under the resources directory
            return new ModelAndView(model, "index.ftl");
        }, new CustomFreeMarkerEngine(configuration));

        // A document reader view
        get("/documentReader", documentApi.getSingleDocumentReadView);

        // A corpus World View
        get("/globe", documentApi.get3dGlobe);

        // Gets a corpus inspector view
        get("/corpus", documentApi.getCorpusInspectorView);

        // Define default exception handler. This shows an error view then in the body.
        ExceptionHandler<Exception> defaultExceptionHandler = (exception, request, response) -> {
            logger.error("Unknown error handled in API - returning default error view.", exception);
            response.status(500);
            response.body(new CustomFreeMarkerEngine(configuration).render(new ModelAndView(null, "defaultError.ftl")));
        };

        // API routes
        path("/api", () -> {

            exception(Exception.class, defaultExceptionHandler);

            before("/*", (req, res) -> {
            });

            path("/search", () -> {
                post("/default", searchApi.search);
                post("/semanticRole", searchApi.semanticRoleSearch);
                get("/active/page", searchApi.activeSearchPage);
                get("/active/sort", searchApi.activeSearchSort);
                get("/semanticRole/builder", searchApi.getSemanticRoleBuilderView);
            });

            path("/corpusUniverse", () -> {
                // Gets a corpus universe view
                get("/new", corpusUniverseApi.getCorpusUniverseView);
                post("/fromSearch", corpusUniverseApi.fromSearch);
                post("/fromCorpus", corpusUniverseApi.fromCorpus);
                get("/nodeInspectorContent", corpusUniverseApi.getNodeInspectorContentView);
            });

            path("/document", () -> {
                get("/reader/pagesList", documentApi.getPagesListView);
            });

            path("/rag", () -> {
                get("/new", ragApi.getNewRAGChat);
                post("/postUserMessage", ragApi.postUserMessage);
                get("/plotTsne", ragApi.getTsnePlot);
            });
        });
    }
}

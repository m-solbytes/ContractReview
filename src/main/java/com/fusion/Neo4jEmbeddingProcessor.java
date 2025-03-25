package com.fusion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fusion.utils.TextEmbeddingGenerator;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.EndpointName;
import com.google.cloud.vertexai.api.PredictRequest;
import com.google.cloud.vertexai.api.PredictionServiceClient;
import com.google.cloud.vertexai.api.PredictionServiceSettings;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class Neo4jEmbeddingProcessor {

    private static final String NEO4J_URI = "bolt://localhost:7687";
    private static final String NEO4J_USER = "neo4j";
    private static final String NEO4J_PASSWORD = "P@$$W0rd";

    private static final String projectId = "neat-vent-381323";
    private static final String location = "us-central1";
    private static final String modelName = "text-embedding-005";

    public static void main(String[] args) {

        Config config = Config.builder().withLogging(Logging.console(Level.INFO)).build();
        try (Driver driver = GraphDatabase.driver(NEO4J_URI, AuthTokens.basic(NEO4J_USER, NEO4J_PASSWORD), config);
             TextEmbeddingGenerator embeddingGenerator =
                     new TextEmbeddingGenerator(projectId, location, modelName);
             Session session = driver.session()) {

            // STEP 1 Create Full Text Indices
            Result result = session.run(
                    "MATCH (e:Excerpt) " +
                            "WHERE e.text IS NOT NULL AND e.embedding IS NULL " +
                            "RETURN id(e) AS nodeId, e.text AS text"
            );

            // Iterate over the results
            while (result.hasNext()) {
                Record record = result.next();
                long nodeId = record.get("nodeId").asLong();
                String text = record.get("text").asString();

                // Generate embedding for the text
                float[] embedding = embeddingGenerator.generateEmbedding(text);
                session.executeWrite(tx -> {
                    tx.run(
                            "MATCH (e) WHERE id(e) = $nodeId " +
                                    "SET e.embedding = $embedding",
                            Values.parameters("nodeId", nodeId, "embedding", embedding)
                    );
                    return null;
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }






}
package com.fusion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.ArrayList;

public class JsonToNeo4jImporter {

    private static final String NEO4J_URI = "bolt://localhost:7687";
    private static final String NEO4J_USER = "neo4j";
    private static final String NEO4J_PASSWORD = "P@$$W0rd";
    private static final String JSON_CONTRACT_FOLDER = "src/main/resources/data/output";

    public static void main(String[] args) {

        Config config = Config.builder().withLogging(Logging.console(Level.INFO)).build();
        try (Driver driver = GraphDatabase.driver(NEO4J_URI, AuthTokens.basic(NEO4J_USER, NEO4J_PASSWORD), config);
             Session session = driver.session()) {
            // STEP 1 Create Full Text Indices
            List<String[]> createFullTextIndices = loadIndexesFromFile("src/main/resources/data/cypher_queries/indexDefinitions.txt");
            createIndexes(session, createFullTextIndices);
            // STEP 2 Create Vector Index
            String createVectorIndexStatement = loadQueryFromFile("src/main/resources/data/cypher_queries/createVectorIndexStatement.txt");
            session.executeWrite(tx -> {
                tx.run(createVectorIndexStatement);
                return null;
            });
            // Get and Print all Indexes
            Result result = session.run("SHOW INDEXES");
            while (result.hasNext()) {
                Record record = result.next();
                System.out.println(record.get("name").asString());
            }

            // Step 3 Load JSON Files

            String createGraphStatement = loadQueryFromFile("src/main/resources/data/cypher_queries/createGraphStatement.txt");
            List<String> jsonContracts = listJsonFiles(JSON_CONTRACT_FOLDER);
            int contractId = 1;

            for (String jsonContract : jsonContracts) {
                String jsonString = new String(Files.readAllBytes(Paths.get(JSON_CONTRACT_FOLDER, jsonContract)));
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode jsonData = objectMapper.readTree(jsonString);
                ((ObjectNode) jsonData.get("agreement")).put("contract_id", contractId);

                Map<String, Object> dataMap = objectMapper.convertValue(jsonData, Map.class);

                session.executeWrite(tx -> {
                    tx.run(createGraphStatement, Values.parameters("data", dataMap));
                    return null;
                });

                contractId++;
            }


            System.out.println("Generating Embeddings for Contract Excerpts...");



        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<String[]> loadIndexesFromFile(String filePath) throws IOException {
        List<String[]> indexes = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(filePath));
        for (String line : lines) {
            String[] parts = line.split(",", 2);
            if (parts.length == 2) {
                indexes.add(new String[]{parts[0].trim(), parts[1].trim()});
            }
        }
        return indexes;
    }

    private static void createIndexes(Session session, List<String[]> indexes) {
        for (String[] index : indexes) {
            String indexName = index[0];
            String indexQuery = index[1];
            session.executeWrite(tx -> {
                tx.run(indexQuery);
                return null;
            });
        }
    }

    private static List<String> listJsonFiles(String folderPath) {
        List<String> jsonFiles = new ArrayList<>();
        File folder = new File(folderPath);
        File[] listOfFiles = folder.listFiles((dir, name) -> name.endsWith(".json"));
        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                jsonFiles.add(file.getName());
            }
        }
        return jsonFiles;
    }

    private static String loadQueryFromFile(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    private static boolean indexExists(Session session, String indexName) {
        Result result = session.run("SHOW INDEXES WHERE name = $index_name", Values.parameters("index_name", indexName));
        return result.hasNext();
    }

    private static String loadJsonContractFolder(String filePath) {
        try {
            return new String(Files.readAllBytes(Paths.get(filePath))).trim();
        } catch (IOException e) {
            e.printStackTrace();
            return "./data/output/";
        }
    }
}
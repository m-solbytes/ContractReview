package com.fusion;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.ContentMaker;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.PartMaker;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import com.google.protobuf.util.JsonFormat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ConvertPDFToJSON {

    public static void main(String[] args) throws IOException {
        // TODO(developer): Replace these variables before running the sample.
        String projectId = "neat-vent-381323";
        String location = "us-central1";
        String modelName = "gemini-2.0-flash-lite-001";
        String inputFilePath = ConvertPDFToJSON.class.getResource("/data/input/AtnInternational.pdf").getPath();

        String output = convertPDFToJSON(inputFilePath, projectId, location, modelName);
        saveJSONToFile(output, inputFilePath);
        System.out.println(output);
    }

    // Analyzes the provided Multimodal input.
    public static String convertPDFToJSON(String inputFilePath, String projectId, String location, String modelName)
            throws IOException {
        String result = "";
        // Initialize client that will be used to send requests. This client only needs
        // to be created once, and can be reused for multiple requests.
        try (VertexAI vertexAI = new VertexAI(projectId, location)) {


                // Load prompts from resources
                String userPrompt;
                try (InputStream inputStream = ConvertPDFToJSON.class.getResourceAsStream("/prompts/contract_extraction_prompt.txt")) {
                    userPrompt = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }

                String systemPrompt;
                try (InputStream inputStream = ConvertPDFToJSON.class.getResourceAsStream("/prompts/system_prompt.txt")) {
                    systemPrompt = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                }

                // Extract PDF text
                String pdfText = extractText(inputFilePath);
                String fullPrompt = systemPrompt + "\n\n" + userPrompt + "\n\n" + pdfText;

            // Build content list
            List<Content> contents = List.of(
//                    Content.newBuilder()
//                            .addParts(Part.newBuilder().setText(systemPrompt).build())
//                            .setRole("system")
//                            .build(),
                    Content.newBuilder()
                            .addParts(Part.newBuilder().setText(fullPrompt).build())
                            .setRole("user")
                            .build()
            );
            GenerativeModel model = new GenerativeModel(modelName, vertexAI);

            GenerateContentResponse response = model.generateContent(contents);
            result  = JsonFormat.printer().print(response);

            for (Candidate  candidate : response.getCandidatesList()) {
                Content content = candidate.getContent();
                for (Part part : content.getPartsList()) {
                    String text = part.getText();
                    result = text.replaceAll("```json\\n|```", "").trim();
                    // Process the text as needed

                }
            }

        }catch (Exception e){
            e.printStackTrace();
        }
        return result;

    }

    private static String extractText(String filePath) throws Exception {
        try (PDDocument doc = PDDocument.load(new File(filePath))) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private static void saveJSONToFile(String json, String inputFilePath) throws IOException {
        String outputFilePath = inputFilePath.replace("/data/input/", "/data/output/").replace(".pdf", ".json");
        outputFilePath = "src/main/resources" + outputFilePath.substring(outputFilePath.indexOf("/data/output/"));
        try (FileWriter fileWriter = new FileWriter(outputFilePath)) {
            fileWriter.write(json);
        }
    }
}
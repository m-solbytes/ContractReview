package com.fusion.utils;

import com.google.cloud.vertexai.api.EndpointName;
import com.google.cloud.vertexai.api.PredictRequest;
import com.google.cloud.vertexai.api.PredictResponse;
import com.google.cloud.vertexai.api.PredictionServiceClient;
import com.google.cloud.vertexai.api.PredictionServiceSettings;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import java.io.IOException;
import java.util.List;

public class TextEmbeddingGenerator implements AutoCloseable {

    private final PredictionServiceClient predictionServiceClient;
    private final EndpointName endpointName;

    public TextEmbeddingGenerator(String projectId, String location, String modelId) throws IOException {
        String endpoint = String.format("%s-aiplatform.googleapis.com:443", location);
        PredictionServiceSettings settings = PredictionServiceSettings.newBuilder().setEndpoint(endpoint).build();
        this.predictionServiceClient = PredictionServiceClient.create(settings);
        this.endpointName = EndpointName.ofProjectLocationPublisherModelName(projectId, location, "google", modelId);
    }

    public float[] generateEmbedding(String text) {
        Value instance = Value.newBuilder()
                .setStructValue(Struct.newBuilder()
                        .putFields("content", Value.newBuilder().setStringValue(text).build())
                        .build())
                .build();

        PredictRequest request = PredictRequest.newBuilder()
                .setEndpoint(endpointName.toString())
                .addInstances(instance)
                .build();

        PredictResponse response = predictionServiceClient.predict(request);

        List<Value> embeddingValues = response.getPredictions(0).getStructValue()
                .getFieldsOrThrow("embeddings").getStructValue()
                .getFieldsOrThrow("values").getListValue()
                .getValuesList();

        double[] doubleArray = embeddingValues.stream()
                .mapToDouble(Value::getNumberValue)
                .toArray();

        float[] floatArray = new float[doubleArray.length];
        for (int i = 0; i < doubleArray.length; i++) {
            floatArray[i] = (float) doubleArray[i];
        }
        return floatArray;
    }

    @Override
    public void close() {
        predictionServiceClient.close();
    }
}
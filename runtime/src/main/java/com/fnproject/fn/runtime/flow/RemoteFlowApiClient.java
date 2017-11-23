package com.fnproject.fn.runtime.flow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fnproject.fn.api.Headers;
import com.fnproject.fn.api.flow.HttpMethod;
import com.fnproject.fn.api.flow.PlatformException;
import com.fnproject.fn.runtime.exception.PlatformCommunicationException;
import com.fnproject.fn.runtime.flow.blobs.BlobApiClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * REST client for accessing the Flow service API
 */
public class RemoteFlowApiClient implements CompleterClient {
    private transient final HttpClient httpClient;
    private final String apiUrlBase;
    private final BlobApiClient blobApiClient;
    public static final String CONTENT_TYPE_JAVA_OBJECT = "application/java-serialized-object";

    public RemoteFlowApiClient(String apiUrlBase, BlobApiClient blobClient, HttpClient httpClient) {
        this.apiUrlBase = Objects.requireNonNull(apiUrlBase);
        this.blobApiClient = Objects.requireNonNull(blobClient);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    @Override
    public FlowId createFlow(String functionId) {
        try {
            APIModel.CreateGraphRequest createGraphRequest = new APIModel.CreateGraphRequest(functionId);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.writeValue(baos, createGraphRequest);
            HttpClient.HttpRequest request = HttpClient.preparePost(apiUrlBase + "/flows").withBody(baos.toByteArray());
            try (HttpClient.HttpResponse resp = httpClient.execute(request)) {
                validateSuccessful(resp);
                APIModel.CreateGraphResponse createGraphResponse = objectMapper.readValue(resp.body, APIModel.CreateGraphResponse.class);
                return new FlowId(createGraphResponse.flowId);
            } catch (Exception e) {
                throw new PlatformCommunicationException("Failed to create flow ", e);
            }
        } catch (IOException e) {
            throw new PlatformException("Failed to create CreateGraphRequest");
        }
    }

    @Override
    public CompletionId supply(FlowId flowId, Serializable supplier, CodeLocation codeLocation) {
        return addStage(APIModel.CompletionOperation.SUPPLY, flowId, supplier, codeLocation);
    }

    @Override
    public CompletionId thenApply(FlowId flowId, CompletionId completionId, Serializable fn, CodeLocation codeLocation) {
        return addChainedStage(APIModel.CompletionOperation.THEN_APPLY, completionId, flowId, fn, codeLocation);
    }

    @Override
    public CompletionId thenCompose(FlowId flowId, CompletionId completionId, Serializable fn, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId whenComplete(FlowId flowId, CompletionId completionId, Serializable fn, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId thenAccept(FlowId flowId, CompletionId completionId, Serializable fn, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId thenRun(FlowId flowId, CompletionId completionId, Serializable fn, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId acceptEither(FlowId flowId, CompletionId completionId, CompletionId alternate, Serializable fn, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId applyToEither(FlowId flowId, CompletionId completionId, CompletionId alternate, Serializable fn, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId thenAcceptBoth(FlowId flowId, CompletionId completionId, CompletionId alternate, Serializable fn, CodeLocation codeLocation) {
        return null;
    }


    public CompletionId createCompletion(FlowId flowId, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId invokeFunction(FlowId flowId, String functionId, byte[] data, HttpMethod method, Headers headers, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId completedValue(FlowId flowId, boolean success, Object value, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId allOf(FlowId flowId, List<CompletionId> deps, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId handle(FlowId flowId, CompletionId completionId, Serializable fn, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId exceptionally(FlowId flowId, CompletionId completionId, Serializable fn, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId exceptionallyCompose(FlowId flowId, CompletionId completionId, Serializable fn, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId thenCombine(FlowId flowId, CompletionId completionId, Serializable fn, CompletionId alternate, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public boolean complete(FlowId flowId, CompletionId completionId, Object value) {
        return false;
    }

    @Override
    public boolean completeExceptionally(FlowId flowId, CompletionId completionId, Throwable value) {
        return false;
    }

    @Override
    public CompletionId anyOf(FlowId flowId, List<CompletionId> cids, CodeLocation codeLocation) {
        return null;
    }

    @Override
    public CompletionId delay(FlowId flowId, long l, CodeLocation codeLocation) {
        return null;
    }

    // wait for completion  -> result
    @Override
    public Object waitForCompletion(FlowId flowId, CompletionId id, ClassLoader ignored, long timeout, TimeUnit unit) throws TimeoutException {
        return null;
    }

    @Override
    public Object waitForCompletion(FlowId flowId, CompletionId id, ClassLoader ignored) {
        return null;
    }

    public void commit(FlowId flowId) {
        try (HttpClient.HttpResponse response = httpClient.execute(HttpClient.preparePost(apiUrlBase + "/graph/" + flowId.getId() + "/commit"))) {
            validateSuccessful(response);
        } catch (Exception e) {
            throw new PlatformException(e);
        }
    }

    @Override
    public void addTerminationHook(FlowId flowId, Serializable code, CodeLocation codeLocation) {
    }

    private static void validateSuccessful(HttpClient.HttpResponse response) {
        if (!isSuccessful(response)) {
            try {
                String body = response.entityAsString();
                throw new PlatformException(String.format("Received unexpected response (%d) from " +
                   "completer: %s", response.getStatusCode(), body == null ? "Empty body" : body));
            } catch (IOException e) {
                throw new PlatformException(String.format("Received unexpected response (%d) from " +
                   "completer. Could not read body.", response.getStatusCode()), e);
            }
        }
    }

    private static boolean isSuccessful(HttpClient.HttpResponse response) {
        return response.getStatusCode() == 200 || response.getStatusCode() == 201;
    }

    private CompletionId addStage(APIModel.CompletionOperation operation, FlowId flowId, Serializable supplier, CodeLocation codeLocation) {
        try {
            APIModel.AddStageRequest addStageRequest = new APIModel.AddStageRequest();
            byte[] serialized = SerUtils.serialize(supplier);
            addStageRequest.closure = blobApiClient.writeBlob(flowId.getId(), serialized, CONTENT_TYPE_JAVA_OBJECT);
            addStageRequest.operation = operation;
            addStageRequest.codeLocation = codeLocation.getLocation();
            addStageRequest.callerId = FlowRuntimeGlobals.getCurrentCompletionId().map(CompletionId::toString).orElse(null);

            return executeAddStageRequest(flowId, addStageRequest);
        } catch (IOException e) {
            throw new PlatformException("Failed to create AddStageRequest");
        }
    }

    private CompletionId addChainedStage(APIModel.CompletionOperation operation, CompletionId other, FlowId flowId, Serializable supplier, CodeLocation codeLocation) {
        try {
            APIModel.AddStageRequest addStageRequest = new APIModel.AddStageRequest();
            byte[] serialized = SerUtils.serialize(supplier);
            addStageRequest.closure = blobApiClient.writeBlob(flowId.getId(), serialized, CONTENT_TYPE_JAVA_OBJECT);

            addStageRequest.operation = operation;
            addStageRequest.codeLocation = codeLocation.getLocation();
            addStageRequest.callerId = FlowRuntimeGlobals.getCurrentCompletionId().map(CompletionId::toString).orElse(null);
            addStageRequest.deps = Collections.singletonList(other.getId());

            return executeAddStageRequest(flowId, addStageRequest);
        } catch (IOException e) {
            throw new PlatformException("Failed to create AddStageRequest");
        }
    }

    private CompletionId executeAddStageRequest(FlowId flowId, APIModel.AddStageRequest addStageRequest) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(baos, addStageRequest);

        HttpClient.HttpRequest request = HttpClient.preparePost(apiUrlBase + "/flows/" + flowId.getId() + "/stage").withBody(baos.toByteArray());
        try (HttpClient.HttpResponse resp = httpClient.execute(request)) {
            validateSuccessful(resp);
            APIModel.AddStageResponse addStageResponse = objectMapper.readValue(resp.body, APIModel.AddStageResponse.class);
            return new CompletionId(addStageResponse.stageId);
        } catch (Exception e) {
            throw new PlatformCommunicationException("Failed to add stage ", e);
        }
    }
}

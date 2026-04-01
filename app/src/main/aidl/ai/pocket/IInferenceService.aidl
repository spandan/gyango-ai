package ai.pocket;

import ai.pocket.IInferenceCallback;

interface IInferenceService {
    boolean isReady();
    void generateStreaming(String text, String action, IInferenceCallback callback);
}

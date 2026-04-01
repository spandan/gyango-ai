package ai.pocket;

oneway interface IInferenceCallback {
    void onToken(String token);
    void onComplete();
    void onError(String message);
}

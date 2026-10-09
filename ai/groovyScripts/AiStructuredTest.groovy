import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.ai.AiWorker

final String MODULE = 'AiStructuredTest.groovy'

def messages = [
    [role: "user", content: "Return a greeting with a single word."]
]

def schema = [
    word: "string"
]

try {
    Map result = AiWorker.generateStructured(dctx, messages, schema)
    if (!result || !result.containsKey("word")) {
        Debug.logError("AI structured smoke test failed: response missing 'word' key. Got: " + result, MODULE)
        return error("AI structured smoke test failed: missing 'word' key in response")
    }
    Debug.logInfo("AI structured smoke test response: " + result, MODULE)
    return success("AI structured smoke test passed: " + result)
} catch (Exception e) {
    Debug.logError(e, "AI structured smoke test failed", MODULE)
    return error("AI structured smoke test failed: " + e.getMessage())
}

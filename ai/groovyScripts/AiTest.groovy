import org.apache.ofbiz.base.util.Debug
import org.apache.ofbiz.ai.AiWorker

final String MODULE = 'AiTest.groovy'

def messages = [
    [role: "user", content: "Say hello in one word."]
]

try {
    String response = AiWorker.generate(dctx, messages)
    Debug.logInfo("AI smoke test response: " + response, MODULE)
    return success("AI smoke test passed: " + response)
} catch (Exception e) {
    Debug.logError(e, "AI smoke test failed", MODULE)
    return error("AI smoke test failed: " + e.getMessage())
}

package com.blackcat.remote

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class OpenAiKeyCheckerTest {
    private val model=PlanCodec.DEFAULT_MODEL

    @Test fun validModelMetadataVerifiesAuthenticationWithExplicitLimits() {
        val result=OpenAiKeyChecker.interpret(200,model,
            JSONObject().put("object","model").put("id",model).toString())
        assertTrue(result.verified)
        assertEquals(KeyCheckStatus.VERIFIED,result.status)
        assertTrue(result.message.contains("Billing"))
        assertTrue(result.message.contains("not tested"))
    }

    @Test fun authenticationRejectionIsNotModelAccessFailure() {
        val result=OpenAiKeyChecker.interpret(401,model,"")
        assertEquals(KeyCheckStatus.REJECTED_KEY,result.status)
        assertFalse(result.verified)
    }

    @Test fun forbiddenDoesNotClaimKeyIsIncorrect() {
        val result=OpenAiKeyChecker.interpret(403,model,"")
        assertEquals(KeyCheckStatus.FORBIDDEN,result.status)
        assertFalse(result.verified)
        assertTrue(result.message.contains("does not prove"))
    }

    @Test fun unavailableModelIsDistinctFromRejectedKey() {
        assertEquals(KeyCheckStatus.MODEL_UNAVAILABLE,OpenAiKeyChecker.interpret(404,model,"").status)
    }

    @Test fun rateLimitCannotProduceVerification() {
        val result=OpenAiKeyChecker.interpret(429,model,"")
        assertEquals(KeyCheckStatus.RATE_LIMITED,result.status)
        assertFalse(result.verified)
    }

    @Test fun redirectCannotProduceVerificationOrEchoProviderText() {
        val result=OpenAiKeyChecker.interpret(302,model,"fake secret-bearing response")
        assertFalse(result.verified)
        assertFalse(result.message.contains("secret"))
        assertTrue(result.message.contains("blocked"))
    }

    @Test fun mismatchedModelMetadataIsNotSuccess() {
        assertEquals(KeyCheckStatus.INVALID_RESPONSE,OpenAiKeyChecker.interpret(200,model,
            JSONObject().put("object","model").put("id","other-model").toString()).status)
    }

    @Test fun malformedSuccessBodyIsNotSuccess() {
        assertFalse(OpenAiKeyChecker.interpret(200,model,"not json").verified)
        assertFalse(OpenAiKeyChecker.interpret(200,model,"{}").verified)
    }

    @Test fun fixedModelEndpointRequiresHttps() {
        assertEquals("https://api.openai.com/v1/models/",OpenAiKeyChecker.MODELS_ENDPOINT)
    }

    @Test(expected=IllegalArgumentException::class) fun unsupportedModelCannotBeVerified() {
        OpenAiKeyChecker.interpret(200,"other-model", "{}")
    }
}

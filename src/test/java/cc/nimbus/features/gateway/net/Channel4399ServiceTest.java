package cc.nimbus.features.gateway.net;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class Channel4399ServiceTest {
    @Test
    void parsesJsonpAndSdkLoginForm() throws Exception {
        JsonObject value = Channel4399Service.parseJsonp(
                "callback({\"code\":0,\"data\":{\"sdk_login_data\":\"uid=42&token=a%2Bb&time=7&username=Nimbus+User\"}})",
                "callback"
        );
        Map<String, String> form = Channel4399Service.parseForm(
                value.getAsJsonObject("data").get("sdk_login_data").getAsString()
        );

        assertEquals("42", form.get("uid"));
        assertEquals("a+b", form.get("token"));
        assertEquals("Nimbus User", form.get("username"));
    }

    @Test
    void rejectsWrongJsonpCallback() {
        assertThrows(
                GatewayProtocolException.class,
                () -> Channel4399Service.parseJsonp("other({\"code\":0})", "callback")
        );
    }

    @Test
    void extracts4399LoginErrorWithoutHtmlTags() {
        assertEquals(
                "账号或密码错误",
                Channel4399Service.extractLoginError("<div class=\"login_err_tip\"><b>账号或密码错误</b></div>")
        );
        assertEquals("账号异常", Channel4399Service.extractLoginError("账号异常"));
    }

    @Test
    void keepsDowngradeRedirectAuthenticationParameters() throws Exception {
        URI target = Channel4399Service.redirectTarget(
                URI.create("https://ptlogin.4399.com/ptlogin/checkKidLoginUserCookie.do?a=b"),
                302,
                "http://cdn.h5wan.4399sj.com/microterminal-h5-frame?sig=s&uid=42&time=7"
        );

        assertEquals("sig=s&uid=42&time=7", target.getRawQuery());
    }

    @Test
    void encodesFormValuesLikeNelUriEscapeDataString() {
        assertEquals("%21%2A%27%28%29~%20", Channel4399Service.formEncode("!*'()~ "));
    }

    @Test
    void generatesCurrentNelCaptchaIdentifier() {
        String captchaId = Channel4399Service.newCaptchaId();

        assertEquals(40, captchaId.length());
        org.junit.jupiter.api.Assertions.assertTrue(captchaId.matches("[0-9a-f]{40}"));
    }

    @Test
    void generatesNimbusPeCaptchaIdentifier() {
        String captchaId = Channel4399Service.newCommonCaptchaId();

        assertEquals(32, captchaId.length());
        org.junit.jupiter.api.Assertions.assertTrue(captchaId.matches("[0-9A-F]{32}"));
    }

    @Test
    void extractsCommon4399LoginError() {
        assertEquals(
                "验证码错误",
                Channel4399Service.extractCommonLoginError(
                        "<html><p class=\"login_err_msg\"><b>验证码错误</b></p></html>"
                )
        );
    }

    @Test
    void buildsCurrentNimbusPeLoginParameters() {
        Map<String, String> parameters = Channel4399Service.commonLoginParameters(
                "client", "state", "ref", "NIMBUS", "password", "captcha", "captcha-id"
        );

        assertEquals(15, parameters.size());
        assertEquals("false", parameters.get("isVaildRealname"));
        assertEquals("0", parameters.get("sec"));
        assertEquals("NIMBUS", parameters.get("username"));
        assertEquals("client", parameters.get("client_id"));
        assertEquals("ref", parameters.get("ref"));
        assertEquals(
                java.util.List.of(
                        "auth_action", "bizId", "captcha", "captcha_id", "client_id", "isInputRealname",
                        "isVaildRealname", "password", "redirect_uri", "ref", "response_type", "scope",
                        "sec", "state", "username"
                ),
                parameters.keySet().stream().toList()
        );
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put("a", "");
        values.put("b", "x y");
        assertEquals("a=&b=x%20y", Channel4399Service.formIncludingEmpty(values));
        assertEquals("a=&b=x+y", Channel4399Service.commonForm(values));
    }
}

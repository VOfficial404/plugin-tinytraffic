package io.github.vofficial404.tinytraffic;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.vofficial404.tinytraffic.setting.BasicSetting;
import io.github.vofficial404.tinytraffic.setting.StatsSetting;
import io.github.vofficial404.tinytraffic.setting.TrackerSetting;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Halo only reads these files when the plugin is installed, so a mistake in them would not fail the build. */
class ResourcesTest {

    private static List<Map<String, Object>> documents(String resource) throws IOException {
        try (InputStream in = ResourcesTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertThat(in).as(resource).isNotNull();
            var documents = new ArrayList<Map<String, Object>>();
            for (var document : new Yaml().loadAll(in)) {
                documents.add(cast(document));
            }
            return documents;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }

    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        return cast(parent.get(key));
    }

    @Test
    void manifestPointsAtTheSettingAndTheLogo() throws IOException {
        var plugin = documents("plugin.yaml").get(0);
        var setting = documents("extensions/settings.yaml").get(0);
        var spec = map(plugin, "spec");

        assertThat(spec.get("settingName")).isEqualTo(map(setting, "metadata").get("name"));
        assertThat(spec.get("configMapName")).isEqualTo("plugin-tinytraffic-configmap");
        assertThat(ResourcesTest.class.getClassLoader().getResource((String) spec.get("logo")))
                .isNotNull();
    }

    @Test
    void settingFormHasAFieldForEveryRecordComponent() throws IOException {
        var setting = documents("extensions/settings.yaml").get(0);
        List<Map<String, Object>> forms = cast(map(setting, "spec").get("forms"));

        assertThat(forms).extracting(form -> form.get("group")).containsExactly("basic", "tracker", "stats");
        assertThat(fieldNames(forms.get(0))).containsExactlyInAnyOrderElementsOf(components(BasicSetting.class));
        assertThat(fieldNames(forms.get(1))).containsExactlyInAnyOrderElementsOf(components(TrackerSetting.class));
        assertThat(fieldNames(forms.get(2))).containsExactlyInAnyOrderElementsOf(components(StatsSetting.class));
    }

    @Test
    void secretFieldAsksForTheKeyTheServerReads() throws IOException {
        var setting = documents("extensions/settings.yaml").get(0);
        List<Map<String, Object>> forms = cast(map(setting, "spec").get("forms"));
        List<Map<String, Object>> schema = cast(forms.get(2).get("formSchema"));
        var secret = schema.stream()
                .filter(field -> "secret".equals(field.get("$formkit")))
                .findFirst()
                .orElseThrow();
        List<Map<String, Object>> requiredKeys = cast(secret.get("requiredKeys"));

        assertThat(requiredKeys).extracting(key -> key.get("key")).containsExactly(StatsSetting.SECRET_KEY);
    }

    @Test
    void addressPatternOfTheFormAgreesWithTheServer() throws IOException {
        var setting = documents("extensions/settings.yaml").get(0);
        List<Map<String, Object>> forms = cast(map(setting, "spec").get("forms"));
        List<Map<String, Object>> schema = cast(forms.get(1).get("formSchema"));
        var field = schema.stream()
                .filter(candidate -> "scriptUrl".equals(candidate.get("name")))
                .findFirst()
                .orElseThrow();
        List<List<String>> validation = cast(field.get("validation"));
        var literal = validation.get(0).get(1);
        // FormKit takes a regular expression written as /…/.
        assertThat(literal).startsWith("/").endsWith("/");
        var pattern = Pattern.compile(literal.substring(1, literal.length() - 1));

        for (var accepted : List.of("/stats/js/script.js", "https://cdn.example.com/s.js", "http://a.example/s.js")) {
            assertThat(pattern.matcher(accepted).find()).as(accepted).isTrue();
            assertThat(TinytrafficUrls.scriptReference(accepted)).as(accepted).isPresent();
        }
        for (var rejected : List.of("//evil.example/s.js", "s.js", "javascript:alert(1)")) {
            assertThat(pattern.matcher(rejected).find()).as(rejected).isFalse();
            assertThat(TinytrafficUrls.scriptReference(rejected)).as(rejected).isEmpty();
        }
    }

    @Test
    void roleTemplateGrantsReadAccessToTheStatsApiOnly() throws IOException {
        var roles = documents("extensions/role-templates.yaml");

        assertThat(roles).hasSize(1);
        var role = roles.get(0);
        assertThat(map(map(role, "metadata"), "labels")).containsEntry("halo.run/role-template", "true");
        List<Map<String, Object>> rules = cast(role.get("rules"));
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0))
                .containsEntry("apiGroups", List.of("console.api.tinytraffic.halo.run"))
                .containsEntry("resources", List.of("stats"))
                .containsEntry("verbs", List.of("get"));
    }

    private static List<Object> fieldNames(Map<String, Object> form) {
        List<Map<String, Object>> schema = cast(form.get("formSchema"));
        return schema.stream().map(field -> field.get("name")).toList();
    }

    private static List<Object> components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(component -> (Object) component.getName())
                .toList();
    }
}

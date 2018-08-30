package io.jenkins.plugins.qrebel;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class QRebelData {
    private final String appName;
    private final String target;
    private final JSONObject issues;
    private final JSONArray entryPoints;

    private QRebelData(String appName, String target, JSONObject issues, JSONArray entryPoints) {
        this.appName = appName;
        this.target = target;
        this.issues = issues;
        this.entryPoints = entryPoints;
    }

    public static QRebelData parse(String json) {
        JSONObject jsonObject = (JSONObject) JSONValue.parse(json);

        String appName = (String) jsonObject.get("appName");
        String target = (String) jsonObject.get("target");
        JSONObject issues = ((JSONObject) jsonObject.get("issuesCount"));
        JSONArray entryPoints = (JSONArray) jsonObject.get("entryPoints");

        return new QRebelData(appName, target, issues, entryPoints);
    }

    public Long getExceptionCount() {
        return parseIssueCount("EXCEPTIONS");
    }

    public Long getDurationCount() {
        return parseIssueCount("DURATION");
    }

    public Long getIOCount() {
        return parseIssueCount("IO");
    }

    public String getAppName() {
        return appName;
    }

    public String getTarget() {
        return target;
    }

    public Optional<List<String>> getEntryPointNames() {
        List<String> entryPointNames = (List<String>) entryPoints.stream()
                .map(e -> ((JSONObject) e).get("name"))
                .collect(Collectors.toList());

        return Optional.of(entryPointNames);
    }

    private Long parseIssueCount(String name) {
        Long value = (Long) issues.get(name);
        return value != null ? value : 0;
    }
}

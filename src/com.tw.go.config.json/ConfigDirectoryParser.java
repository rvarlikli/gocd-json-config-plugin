package com.tw.go.config.json;

import com.google.gson.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigDirectoryParser {
    private ConfigDirectoryScanner scanner;
    private JsonFileParser parser;
    private String pipelinePattern;
    private String environmentPattern;

    public ConfigDirectoryParser(ConfigDirectoryScanner scanner, JsonFileParser parser, String pipelinePattern, String environmentPattern) {

        this.scanner = scanner;
        this.parser = parser;
        this.pipelinePattern = pipelinePattern;
        this.environmentPattern = environmentPattern;
    }

    public JsonConfigCollection parseDirectory(File baseDir) throws Exception {
        JsonConfigCollection config = new JsonConfigCollection();
        for (String environmentFile : scanner.getFilesMatchingPattern(baseDir, environmentPattern)) {
            try {
                JsonElement env = parser.parseFile(new File(baseDir, environmentFile));
                if(env == null || env.isJsonNull())
                {
                    PluginError error = new PluginError(
                            String.format("Environment file is empty"),
                            environmentFile);
                    config.addError(error);
                }
                else if(env.equals(new JsonObject()))
                {
                    PluginError error = new PluginError(
                            String.format("Environment definition is empty"),
                            environmentFile);
                    config.addError(error);
                }
                else
                    config.addEnvironment(env,environmentFile);
            }
            catch (JsonParseException parseException)
            {
                PluginError error = new PluginError(
                        String.format("Failed to parse environment file as JSON: %s",parseException.getMessage()),
                        environmentFile);
                config.addError(error);
            }
        }

        for (String pipelineFile : scanner.getFilesMatchingPattern(baseDir, pipelinePattern)) {
            String pipelineGroup = pipelineFile.substring(0,pipelineFile.indexOf("."));
            try {
                JsonElement pipe = parser.parseFile(new File(baseDir, pipelineFile));
                if(pipe == null || pipe.isJsonNull())
                {
                    PluginError error = new PluginError(
                            String.format("Pipeline file is empty"),
                            pipelineFile);
                    config.addError(error);
                }
                else if(pipe.equals(new JsonObject()))
                {
                    PluginError error = new PluginError(
                            String.format("Pipeline definition is empty"),
                            pipelineFile);
                    config.addError(error);
                }
                else {
                    pipe = fixJson(pipe,pipelineGroup);
                    config.addPipeline(pipe, pipelineFile);
                }
            }
            catch (JsonParseException parseException)
            {
                PluginError error = new PluginError(
                        String.format("Failed to parse pipeline file as JSON: %s",parseException.getMessage()),
                        pipelineFile);
                config.addError(error);
            }
        }

        return config;
    }

    public JsonElement fixJson(JsonElement jsonElement, String pipelineGroup)
    {
        jsonElement.getAsJsonObject().addProperty("group",pipelineGroup);

        ///removing attributes in materials section
        JsonElement materialsElement = jsonElement.getAsJsonObject().get("materials");
        JsonArray materialsArray = materialsElement.getAsJsonArray();
        for(int i=0 ; i< materialsArray.size();i++){
            JsonElement materialObject =  materialsArray.get(i);
            JsonObject attributesObject = materialObject.getAsJsonObject().get("attributes").getAsJsonObject();
            Set<Map.Entry<String, JsonElement>> entries = attributesObject.entrySet();//will return members of your object
            for (Map.Entry<String, JsonElement> entry: entries) {
                materialObject.getAsJsonObject().add(entry.getKey(), entry.getValue());
            }
            materialObject.getAsJsonObject().remove("attributes");
        }
        /// stages approval section
        JsonElement stagesElement = jsonElement.getAsJsonObject().get("stages");
        JsonArray stagesArray = stagesElement.getAsJsonArray();
        for(int i= 0; i<stagesArray.size(); i++){
            JsonElement stagesObject = stagesArray.get(i);
            JsonElement approvalObject = stagesObject.getAsJsonObject().get("approval");
            JsonObject authorizationObject = approvalObject.getAsJsonObject().get("authorization").getAsJsonObject();
            Set<Map.Entry<String, JsonElement>> entries = authorizationObject.entrySet();
            for (Map.Entry<String, JsonElement> entry: entries) {
                approvalObject.getAsJsonObject().add(entry.getKey(), entry.getValue());
            }
            approvalObject.getAsJsonObject().remove("authorization");

            JsonElement jobsElement = stagesObject.getAsJsonObject().get("jobs");
            JsonArray jobsArray = jobsElement.getAsJsonArray();
            for(int j= 0; j < jobsArray.size(); j++){
                JsonElement jobsObject = jobsArray.get(j);
                JsonElement jobsTasksElement = jobsObject.getAsJsonObject().get("tasks");
                JsonArray jobsTasksArray = jobsTasksElement.getAsJsonArray();

                for (int k= 0; k< jobsTasksArray.size(); k++ )
                {
                    JsonElement tasksObject = jobsTasksArray.get(k);
                    JsonObject tasksAttributesObject = tasksObject.getAsJsonObject().get("attributes").getAsJsonObject();
                    Set<Map.Entry<String, JsonElement>> entriesAttributes = tasksAttributesObject.entrySet();
                    for (Map.Entry<String, JsonElement> entry: entriesAttributes) {
                        if(entry.getKey().equals("run_if"))
                        {
                            JsonArray tempValue = entry.getValue().getAsJsonArray();
                            entry.setValue(tempValue.get(0));
                        }
                        tasksObject.getAsJsonObject().add(entry.getKey(), entry.getValue());
                    }
                    tasksObject.getAsJsonObject().remove("attributes");
                }
            }
        }
        return jsonElement;
    }
}

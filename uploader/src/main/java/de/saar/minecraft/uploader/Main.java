package de.saar.minecraft.uploader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.DSLContext;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;

public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            logger.error("Missing csv file with arguments {}", (Object) args);
            return;
        }
        String csvFile = args[0];

        UploaderConfiguration config;
        try {
            config = UploaderConfiguration.loadYaml(new FileReader("config.yml"));
        } catch (FileNotFoundException e) {
            logger.error("Configuration file not found. {}", e.getMessage());
            return;
        }

        QuestionnaireUploader uploader = new QuestionnaireUploader(config);
        List<QuestionnaireUploader.Response> responses = uploader.readForm(csvFile);
        DSLContext jooq = uploader.setupDatabase();
        uploader.updateForm(jooq, responses);


    }
}

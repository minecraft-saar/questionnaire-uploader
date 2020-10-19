package de.saar.minecraft.uploader;

import com.google.protobuf.InvalidProtocolBufferException;
import com.opencsv.CSVReader;

import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.saar.minecraft.broker.db.GameLogsDirection;
import de.saar.minecraft.broker.db.GameStatus;
import de.saar.minecraft.broker.db.Tables;
import de.saar.minecraft.broker.db.tables.records.GameLogsRecord;
import de.saar.minecraft.broker.db.tables.records.GamesRecord;
import de.saar.minecraft.broker.db.tables.records.QuestionnairesRecord;
import de.saar.minecraft.shared.TextMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.exception.TooManyRowsException;
import org.jooq.impl.DSL;

public class QuestionnaireUploader {

    private UploaderConfiguration config;
    static private Logger logger = LogManager.getLogger(QuestionnaireUploader.class);

    QuestionnaireUploader(UploaderConfiguration config) {
        this.config = config;
    }

    DSLContext setupDatabase() {
        var url = config.getUrl();
        var user = config.getUser();
        var password = config.getPassword();

        try {
            Connection conn = DriverManager.getConnection(url, user, password);
            DSLContext ret = DSL.using(
                    conn,
                    SQLDialect.valueOf("MYSQL")
            );
            logger.info("Connected to database at {}.", url);
            return ret;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    List<Response> readForm(String filename) {
        List<Response> responses = new ArrayList<>();
        try {
            CSVReader reader = new CSVReader(new FileReader(filename));
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd h:m:s a", Locale.US);
            String[] line;

            // Get header
            String[] header = reader.readNext();
            // Check header
            if ((!header[0].equals("Timestamp"))
                    || (!header[2].equals("Please enter your Prolific ID"))
                    || (!header[3].equals("Please enter your Minecraft username"))
            ) {
                logger.error("Expected format: \"Timestamp\",\"[...]Please consent below\",\"Please enter your Prolific " +
                        "ID\",\"Please enter your Minecraft username\",\"Please enter the secret phrase[...],...");
                throw new RuntimeException("Wrong csv format");
            }
            logger.info("header {} - {}", header[0], header[header.length-1]);
            while ((line = reader.readNext()) != null) {
                List<Question> questions = new ArrayList<>();

                LocalDateTime timestamp;
                try {
                    // Format: 2020/06/08 3:43:15 PM OEZ -- strip the " OEZ" at the end.
                    String timeStampString = line[0].substring(0, line[0].length()-4);
                    timestamp = LocalDateTime.parse(timeStampString, dateTimeFormatter);
                } catch(Exception e) {  // TODO: less generic
                    logger.error(e.getMessage());
                    throw new RuntimeException(e);
                }

                String prolificId = line[2];
                String playerName = line[3];
                for (int i = 5; i < line.length; i++) {
                    questions.add(new Question(header[i], line[i]));
                    logger.debug("{}: {}", header[i], line[i]);
                }
                responses.add(new Response(timestamp, prolificId, playerName, questions));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return responses;
    }

    void updateForm(DSLContext jooq, List<Response> responses) {
        for (Response entry: responses) {
            logger.info("Player name {}", entry.playerName);
            // Get game id from playername
            GamesRecord game = null;
            try {
                game = jooq.selectFrom(Tables.GAMES)
                        .where(Tables.GAMES.PLAYER_NAME.equal(entry.playerName))
                        .and(Tables.GAMES.STATUS.equal(GameStatus.Finished))
                        .fetchOne();
            } catch (TooManyRowsException e) {
                logger.warn("There are several finished games for player {}", entry.playerName);
                game = jooq.selectFrom(Tables.GAMES)
                        .where(Tables.GAMES.PLAYER_NAME.equal(entry.playerName))
                        .and(Tables.GAMES.STATUS.equal(GameStatus.Finished))
                        .fetchAny();
            }

            if (game == null) {
                game = jooq.selectFrom(Tables.GAMES)
                        .where(Tables.GAMES.PLAYER_NAME.equal(entry.playerName))
                        .fetchAny();
                if (game == null) {
                    logger.warn("Player {} is not in database", entry.playerName);
                } else {
                    logger.warn("Player {} has only unfinished games", entry.playerName);
                }
                continue;
            }
            int gameId = game.getId();
            logger.info("game id {}", gameId);

            // Check if there is already a questionnaire
            Result<QuestionnairesRecord> records = jooq.selectFrom(Tables.QUESTIONNAIRES)
                    .where(Tables.QUESTIONNAIRES.GAMEID.equal(gameId))
                    .fetch();
            if (records.isNotEmpty()) {
                logger.warn("There is already a questionnaire for player {}. Skipping", entry.playerName);
                continue;
            }

            // Add two entries in GAME_LOGS and entry in QUESTIONNAIRE
            for (Question question: entry.questions) {
                String questionMessage = getMessageString(gameId, question.question);

                // Question
                GameLogsRecord questionRecord = jooq.newRecord(Tables.GAME_LOGS);
                questionRecord.setGameid(gameId);
                questionRecord.setDirection(GameLogsDirection.PassToClient);
                questionRecord.setMessageType("TextMessage");
                questionRecord.setMessage(questionMessage);
                questionRecord.setTimestamp(entry.timestamp);
                questionRecord.store();

                String answerMessage = getMessageString(gameId, question.answer);

                // Answer
                GameLogsRecord answerRecord = jooq.newRecord(Tables.GAME_LOGS);
                answerRecord.setGameid(gameId);
                answerRecord.setDirection(GameLogsDirection.FromClient);
                answerRecord.setMessageType("TextMessage");
                answerRecord.setMessage(answerMessage);
                answerRecord.setTimestamp(entry.timestamp);
                answerRecord.store();

                // Questionnaire
                var record = jooq.newRecord(Tables.QUESTIONNAIRES);
                String answer = question.answer;
                if (answer.length() > 4999) {
                    answer = answer.substring(0, 4999);
                }
                record.setAnswer(answer);
                record.setGameid(gameId);
                record.setQuestion(question.question);
                record.setTimestamp(entry.timestamp);
                record.store();
            }
        }
    }

    public void anonymize(DSLContext jooq) {
        List<String> userNames = jooq.
                select(Tables.GAMES.PLAYER_NAME).
                from(Tables.GAMES).
                groupBy(Tables.GAMES.PLAYER_NAME).
                fetch(Tables.GAMES.PLAYER_NAME);
        for (String uname: userNames) {
            jooq.update(Tables.GAMES)
                    .set(Tables.GAMES.PLAYER_NAME, "PLAYER_" + (userNames.indexOf(uname) + 1))
                    .where(Tables.GAMES.PLAYER_NAME.eq(uname))
                    .execute();
        }
        jooq.update(Tables.GAMES)
                .set(Tables.GAMES.CLIENT_IP, "REDACTED")
                .execute();
    }

    private String getMessageString(int gameId, String input) {
        TextMessage questionMessage = TextMessage.newBuilder()
                .setGameId(gameId)
                .setText(input)
                .build();

        String messageStr = "";
        try {
            messageStr = com.google.protobuf.util.JsonFormat.printer().print(questionMessage);
        } catch (InvalidProtocolBufferException e) {
            logger.error("could convert message to json: " + questionMessage);
        }
        return messageStr;
    }

    public static class Response {
        public LocalDateTime timestamp;
        public String prolificId;
        public String playerName;
        public List<Question> questions;

        Response (LocalDateTime timestamp, String prolificId, String playerName, List<Question> questions) {
            this.timestamp = timestamp;
            this.prolificId = prolificId;
            this.playerName = playerName;
            this.questions = questions;
        }
    }

    public static class Question {
        public String question;
        public String answer;

        Question(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }
}

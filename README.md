# questionnaire-uploader
Integrates questionnaire responses from the Google Form into the database.

## Usage
1. Create config.yml with the same url, user and password with which the broker accesses the database
2. `./gradlew shadowJar`
3. `java -jar build/libs/uploader-1.0-SNAPSHOT-all.jar <path/to/Minecraft.csv>`

## Notes
- If there are several games for one player, only the first game is updated (with a warning)
- If there is no game for a player in the .csv, there is a warning but the other players are updated
- The uploader only appends, not substitutes questionnaires. If the same file is run twice, the responses appear twice in the database


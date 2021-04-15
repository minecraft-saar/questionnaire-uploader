# questionnaire-uploader
Integrates questionnaire responses from the Google Form into the database.

## Usage

The importer is configured using a configuration file (config.yml).
It needs to declare a database URL, user, and passwort, e.g.:

url: "jdbc:mariadb://localhost:3306/MINECRAFT"
user: "minecraft"
password: ""

Create a config.yml with the above data, which should be the same as
the one you use for your analysis (i.e. the URL needs to point to the
database where you store your current games you want matched).

You can build the questionnare uploader with `./gradlew shadowJar` in
the `uploader` directory. You can then run the uploader with
`java -jar build/libs/uploader-1.0-SNAPSHOT-all.jar <path/to/Minecraft.csv>`
where `Minecraft.csv` is the CSV file exported from the Google form.

The uploader anonymizes the data in the database by default.  It
replaces user names with `PLAYER_X`, where X is a unique number per
player.  It also removes the IP addresses.  You can disable this
by adding `--no-anonymize` as the first(!) argument.


## Notes
- If there are several games for one player, only the first game is
  updated (with a warning)
- If there is no game for a player in the .csv, there is a warning but
  the other players are updated
- The uploader only appends, not substitutes questionnaires. If the
  same file is run twice, the responses appear twice in the database.
  This is only true if you use  `--no-anonymize`, as otherwise no 
  matching is possible on the second run.

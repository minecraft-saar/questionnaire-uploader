package de.saar.minecraft.uploader;

import org.yaml.snakeyaml.Yaml;

import java.io.Reader;
import java.util.List;

import org.yaml.snakeyaml.constructor.Constructor;

public class UploaderConfiguration {

    private String url;
    private String user;
    private String password;
    private List<Integer> questions = List.of(4,6,7,8,9,10,11);

    public static UploaderConfiguration loadYaml(Reader reader) {
        Constructor constructor = new Constructor(UploaderConfiguration.class);
        Yaml yaml = new Yaml(constructor);
        return yaml.loadAs(reader, UploaderConfiguration.class);
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public List<Integer> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Integer> questions) {
        this.questions = questions;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

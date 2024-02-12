package org.example;

import org.apache.commons.cli.*;

import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        Map<String, Object> crawlerFields = getCrawlerFields();
        Map<String, Object> argsToValueMap = parseCommandLineArgs(args, crawlerFields);
        Crawler crawler = new Crawler(argsToValueMap);

        crawler.startCrawler();
    }

    private static Map<String, Object> getCrawlerFields() {
        return Arrays.stream(Crawler.class.getFields())
                .collect(Collectors.toMap(Field::getName, Field::getAnnotatedType));
    }

    public static Map<String, Object> parseCommandLineArgs(String[] args, Map<String, Object> crawlerFields) {
        Option argsOption = Option.builder("a").hasArgs().build(); //arguments option (-a requests=...)
        Option settingsOption = Option.builder("s").hasArgs().build(); //settings option (-s RABBITMQ_HOST=...)
        Options options = new Options();
        options.addOption(argsOption);
        options.addOption(settingsOption);
        DefaultParser parser = new DefaultParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            return Arrays.stream(cmd.getOptions())
                    .flatMap(option -> Arrays.stream(option.getValues()))
                    .map(unparsedOptionArgToValue -> unparsedOptionArgToValue.split("="))
                    .filter(parsedOptionArgToValue -> parsedOptionArgToValue.length == 2)
                    .map(optionArgToValue -> {
                        String arg = optionArgToValue[0];
                        String value = optionArgToValue[1];

                        Object fieldType = crawlerFields.get(arg);
                        Object modifiedValue;
                        if (fieldType != null) {
                            try {
                                modifiedValue = switch (fieldType.toString()) {
                                    case "Int" -> Integer.valueOf(value);
                                    case "Long" -> Long.valueOf(value);
                                    case "Boolean" -> Boolean.valueOf(value);
                                    default -> throw new NumberFormatException("Unsupported type: " + fieldType);
                                };
                            } catch (NumberFormatException e) {
                                throw new RuntimeException("Field \"" + arg + "\" must be of type " + fieldType
                                        + " and we cannot cast value \"" + value + "\" to them.");
                            }
                        } else {
                            modifiedValue = value;
                        }

                        return new AbstractMap.SimpleEntry<>(arg, modifiedValue);
                    })
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }


    }

}

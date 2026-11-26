package com.datasphere.migration.mapping;

import com.datasphere.migration.config.MappingProperties;
import com.datasphere.migration.mapping.engine.AbbreviationDictionary;
import com.datasphere.migration.mapping.engine.MappingEngine;
import com.datasphere.migration.mapping.engine.TablePairSuggester;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the deterministic mapping engine. */
@Configuration
public class MappingConfiguration {

    @Bean
    public AbbreviationDictionary abbreviationDictionary(MappingProperties properties) {
        return AbbreviationDictionary.load(properties.abbreviationFile());
    }

    @Bean
    public MappingEngine mappingEngine(MappingProperties properties, AbbreviationDictionary dictionary) {
        return new MappingEngine(properties, dictionary);
    }

    @Bean
    public TablePairSuggester tablePairSuggester(MappingProperties properties, MappingEngine engine) {
        return new TablePairSuggester(properties, engine.nameMatcher());
    }
}

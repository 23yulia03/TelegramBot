package org.example;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class RiskDataStorage {
    private static final Logger logger = LoggerFactory.getLogger(RiskDataStorage.class);
    private static Map<String, Map<String, Integer>> riskData = new HashMap<>();

    static {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            InputStream is = RiskDataStorage.class.getResourceAsStream("/risk_data.json");
            if (is == null) {
                logger.error("Файл risk_data.json не найден в ресурсах");
                throw new RuntimeException("Файл конфигурации не найден");
            }
            riskData = objectMapper.readValue(is, new TypeReference<>() {});
        } catch (IOException e) {
            logger.error("Ошибка загрузки файла конфигурации", e);
            // Загрузка значений по умолчанию
            loadDefaultValues();
        }
    }

    private static void loadDefaultValues() {
        logger.warn("Загрузка значений по умолчанию");
        // Реализация загрузки дефолтных значений
    }

    public static int getScore(String parameterType, String value) {
        Map<String, Integer> thresholds = riskData.get(parameterType);
        return thresholds != null ? thresholds.getOrDefault(value, 0) : 0;
    }
}
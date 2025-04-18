package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class RiskDataStorage {
    private final Map<String, ParameterConfig> parameters;
    private final List<RiskLevel> riskLevels;
    private final ProbabilityFormula probabilityFormula;

    public RiskDataStorage() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = getClass().getResourceAsStream("/risk_config.json");
            if (is == null) {
                throw new FileNotFoundException("Файл конфигурации risk_config.json не найден");
            }

            Config config = mapper.readValue(is, Config.class);

            this.parameters = config.getParameters();
            this.riskLevels = config.getRiskLevels();
            this.probabilityFormula = config.getProbabilityFormula();

        } catch (Exception e) {
            throw new RuntimeException("Ошибка загрузки конфигурации", e);
        }
    }

    public ParameterConfig getParameterConfig(String paramName) {
        return parameters.get(paramName);
    }

    public RiskLevel getRiskLevel(int score) {
        return riskLevels.stream()
                .filter(r -> score >= r.getMinScore() && score <= r.getMaxScore())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный уровень риска"));
    }

    public ProbabilityFormula getProbabilityFormula() {
        return probabilityFormula;
    }

    // Классы для десериализации JSON
    public static class Config {
        @JsonProperty("parameters")
        private Map<String, ParameterConfig> parameters;

        @JsonProperty("risk_levels")
        private List<RiskLevel> riskLevels;

        @JsonProperty("probabilityFormula")
        private ProbabilityFormula probabilityFormula;

        public Map<String, ParameterConfig> getParameters() {
            return parameters;
        }

        public List<RiskLevel> getRiskLevels() {
            return riskLevels;
        }

        public ProbabilityFormula getProbabilityFormula() {
            return probabilityFormula;
        }
    }


    public static class ParameterConfig {
        private List<Range> ranges;
        private String unit;
        private String description;

        public Range findRange(double value) {
            return ranges.stream()
                    .filter(r -> value >= r.getMin() && value <= r.getMax())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Значение вне диапазона"));
        }

        public List<Range> getRanges() {
            return ranges;
        }

        public String getUnit() {
            return unit;
        }

        public String getDescription() {
            return description;
        }
    }

    public static class Range {
        private double min;
        private double max;
        private int score;
        private String comment;

        public double getMin() {
            return min;
        }

        public double getMax() {
            return max;
        }

        public int getScore() {
            return score;
        }

        public String getComment() {
            return comment;
        }
    }

    public static class RiskLevel {
        private int minScore;
        private int maxScore;
        private String diagnosis;
        private String recommendation;
        private String probabilityRange;

        public int getMinScore() {
            return minScore;
        }

        public int getMaxScore() {
            return maxScore;
        }

        public String getDiagnosis() {
            return diagnosis;
        }

        public String getRecommendation() {
            return recommendation;
        }

        public String getProbabilityRange() {
            return probabilityRange;
        }
    }

    public static class ProbabilityFormula {
        @JsonProperty("intercept")
        private double intercept;

        @JsonProperty("ageCoef")
        private double age_coef;

        @JsonProperty("apgarCoef")
        private double apgar_coef;

        @JsonProperty("weightCoef")
        private double weight_coef;

        @JsonProperty("pao2Coef")
        private double pao2_coef;

        @JsonProperty("phCoef")
        private double ph_coef;

        @JsonProperty("malformationsCoef")
        private double malformations_coef;

        @JsonProperty("intubationCoef")
        private double intubation_coef;

        public double calculateLogit(double ph, double age, double apgar, double weight,
                                     double pao2, double malformations, double intubation) {
            return intercept
                    + ph_coef * ph
                    + age_coef * age
                    + apgar_coef * apgar
                    + weight_coef * weight
                    + pao2_coef * pao2
                    + malformations_coef * malformations
                    + intubation_coef * intubation;
        }
    }
}
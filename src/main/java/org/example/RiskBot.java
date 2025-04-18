package org.example;

import io.github.cdimascio.dotenv.Dotenv;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.DecimalFormat;
import java.util.*;

public class RiskBot extends TelegramLongPollingBot {
    private final RiskDataStorage storage;
    private final DecimalFormat df = new DecimalFormat("0.00%");
    private final Map<String, UserState> userStates = new HashMap<>();

    public RiskBot() {
        this.storage = new RiskDataStorage();
    }

    @Override
    public String getBotUsername() {
        return "NeoRisk_bot";
    }

    @Override
    public String getBotToken() {
        Dotenv dotenv = Dotenv.configure()
                .filename("token.env") // если не .env
                .load();
        return dotenv.get("BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        Message message = update.getMessage();
        String chatId = message.getChatId().toString();
        String text = message.getText().trim();

        try {
            UserState userState = userStates.computeIfAbsent(chatId, k -> new UserState());

            if (text.startsWith("/")) {
                handleCommand(chatId, text);
                // Сбрасываем состояние при командах
                if ("/start".equals(text) || "/help".equals(text)) {
                    userState.reset();
                }
            } else {
                handleParameterInput(chatId, text, userState);
            }
        } catch (Exception e) {
            sendErrorResponse(chatId, "Произошла ошибка: " + e.getMessage());
        }
    }

    private void handleCommand(String chatId, String command) throws TelegramApiException {
        switch (command) {
            case "/start":
                sendWelcomeMessage(chatId);
                break;
            case "/help":
                sendHelpMessage(chatId);
                break;
            default:
                sendResponse(chatId, "Неизвестная команда. Введите /help для списка команд.");
        }
    }

    private void handleParameterInput(String chatId, String text, UserState userState) throws TelegramApiException {
        try {
            String paramName = userState.getCurrentParameterName();

            // ✅ Проверка и парсинг значения
            if (paramName.contains("pH крови") || paramName.contains("PaO2")) {
                Double.parseDouble(text); // просто проверка
            } else if (paramName.contains("Возраст") ||
                    paramName.contains("Апгар") ||
                    paramName.contains("Вес")) {
                Integer.parseInt(text);
            } else if (paramName.contains("пороки") || paramName.contains("Интубация")) {
                int value = Integer.parseInt(text);
                if (value != 0 && value != 1) {
                    throw new IllegalArgumentException("Введите 0 или 1.");
                }
            }

            // ✅ Добавляем только если всё прошло успешно
            userState.addParameterValue(text);

            // ✅ Проверяем — завершены ли все параметры
            if (userState.isComplete()) {
                processFinalParameters(chatId, userState);
            } else {
                sendResponse(chatId, "Введите следующий параметр: " + getFormattedParameterPrompt(userState.getCurrentParameterName()));
            }

        } catch (NumberFormatException e) {
            sendResponse(chatId, "⚠️ Неверный формат. Пожалуйста, введите корректное числовое значение.");
        } catch (IllegalArgumentException e) {
            sendResponse(chatId, "⚠️ " + e.getMessage());
        }
    }

    private String getFormattedParameterPrompt(String paramName) {
        switch (paramName) {
            case "pH крови":
                return "pH крови (уровень кислотности крови; например: 7.2)";
            case "Возраст в часах":
                return "Возраст в часах (возраст новорождённого на момент оценки; например: 5)";
            case "Оценка по Апгар":
                return "Оценка по шкале Апгар (от 0 до 10; оценивает состояние ребёнка сразу после рождения, например: 6)";
            case "Вес при рождении":
                return "Вес при рождении в граммах (например: 3200)";
            case "PaO2":
                return "PaO2 в кПа (парциальное давление кислорода в артериальной крови; например: 4.5)";
            case "Врожденные пороки (0 - нет, 1 - да)":
                return "Врожденные пороки: введите 0 (нет пороков) или 1 (есть пороки)";
            case "Интубация (0 - нет, 1 - да)":
                return "Интубация: введите 0 (не была проведена) или 1 (проводилась интубация)";
            default:
                return paramName;
        }
    }

    private void processFinalParameters(String chatId, UserState userState) throws TelegramApiException {
        try {
            List<String> params = userState.parameterValues;
            double pH = Double.parseDouble(params.get(0));
            int возрастЧасы = Integer.parseInt(params.get(1));
            int апгар = Integer.parseInt(params.get(2));
            int вес = Integer.parseInt(params.get(3));
            double paO2 = Double.parseDouble(params.get(4));
            int пороки = Integer.parseInt(params.get(5));
            int интубация = Integer.parseInt(params.get(6));

            int баллы = calculateHermansenScore(pH, возрастЧасы, апгар, вес, paO2, пороки, интубация);
            double вероятность = calculateMortalityProbability(pH, возрастЧасы, апгар, вес, paO2, пороки, интубация);

            String результат = buildAssessmentResponse(баллы, вероятность, pH, возрастЧасы, апгар, вес, paO2, пороки, интубация);
            sendResponse(chatId, результат);

            // Сбрасываем состояние после завершения
            userState.reset();
        } catch (Exception e) {
            sendErrorResponse(chatId, "Ошибка расчета: " + e.getMessage());
            userState.reset();
        }
    }

    private int calculateHermansenScore(double pH, int возрастЧасы, int апгар, int вес,
                                        double paO2, int пороки, int интубация) {
        int баллы = 0;

        // Получаем баллы из конфигурации для каждого параметра
        баллы += storage.getParameterConfig("ph").findRange(pH).getScore();
        баллы += storage.getParameterConfig("age").findRange(возрастЧасы).getScore();
        баллы += storage.getParameterConfig("apgar").findRange(апгар).getScore();
        баллы += storage.getParameterConfig("weight").findRange(вес).getScore();
        баллы += storage.getParameterConfig("pao2").findRange(paO2).getScore();
        баллы += storage.getParameterConfig("malformations").findRange(пороки).getScore();
        баллы += storage.getParameterConfig("intubation").findRange(интубация).getScore();

        return баллы;
    }

    private double calculateMortalityProbability(double pH, int возрастЧасы, int апгар, int вес,
                                                 double paO2, int пороки, int интубация) {
        RiskDataStorage.ProbabilityFormula formula = storage.getProbabilityFormula();
        double logit = formula.calculateLogit(pH, возрастЧасы, апгар, вес, paO2, пороки, интубация);
        return Math.exp(logit) / (1 + Math.exp(logit));
    }

    private String buildAssessmentResponse(int баллы, double вероятность,
                                           double pH, int возрастЧасы, int апгар, int вес,
                                           double paO2, int пороки, int интубация) {
        RiskDataStorage.RiskLevel уровеньРиска = storage.getRiskLevel(баллы);

        StringBuilder ответ = new StringBuilder();
        ответ.append("⚕️ Результаты оценки риска транспортировки ⚕️\n\n");
        ответ.append("▉ Общий балл: ").append(баллы).append(" из 40\n");
        ответ.append("▉ Диагноз: ").append(уровеньРиска.getDiagnosis()).append("\n");
        ответ.append("▉ Вероятность: ").append(df.format(вероятность))
                .append(" (").append(уровеньРиска.getProbabilityRange()).append(")\n\n");

        ответ.append("📋 Детализация параметров:\n");
        ответ.append(formatParameterDetail("pH крови", pH, "ph"));
        ответ.append(formatParameterDetail("Возраст", возрастЧасы, "age"));
        ответ.append(formatParameterDetail("Оценка по Апгар", апгар, "apgar"));
        ответ.append(formatParameterDetail("Вес при рождении", вес, "weight"));
        ответ.append(formatParameterDetail("PaO2", paO2, "pao2"));
        ответ.append(formatParameterDetail("Врожденные пороки", пороки, "malformations"));
        ответ.append(formatParameterDetail("Интубация", интубация, "intubation"));

        ответ.append("\n🚑 Рекомендации:\n").append(уровеньРиска.getRecommendation());

        // Добавлено напоминание
        ответ.append("\n\n🔁 Для нового тестирования введите /start");

        return ответ.toString();
    }

    private String formatParameterDetail(String paramName, double value, String configKey) {
        RiskDataStorage.ParameterConfig config = storage.getParameterConfig(configKey);
        RiskDataStorage.Range range = config.findRange(value);
        return String.format("• %s: %.1f %s - %s (%d баллов)\n",
                paramName, value, config.getUnit(), range.getComment(), range.getScore());
    }

    private void sendWelcomeMessage(String chatId) throws TelegramApiException {
        UserState userState = userStates.computeIfAbsent(chatId, k -> new UserState());
        userState.reset();

        String message = "👶 Добро пожаловать в бот оценки риска транспортировки новорожденных!\n\n" +
                "Бот будет запрашивать параметры по одному.\n\n" +
                "Введите /help для подробной инструкции или начните сразу с ввода параметров. \n\n" +
                "Первый параметр: " + userState.getCurrentParameterName() + "(уровень кислотности крови; например: 7.2)";
        sendResponse(chatId, message);
    }

    private void sendHelpMessage(String chatId) throws TelegramApiException {
        String message = "📋 Инструкция по использованию бота:\n\n" +
                "1. Подготовьте следующие данные пациента:\n" +
                "   - Анализ крови (pH, PaO2)\n" +
                "   - Основные антропометрические данные\n" +
                "   - Информацию о состоянии при рождении\n\n" +
                "2. Введите все 7 параметров через запятую в строгом порядке:\n" +
                "   - pH крови\n" +
                "   - Возраст в часах\n" +
                "   - Оценка по Апгар (1-я минута)\n" +
                "   - Вес при рождении (г)\n" +
                "   - PaO2 (кПа)\n" +
                "   - Наличие пороков (0/1)\n" +
                "   - Интубация (0/1)\n\n" +
                "3. Бот рассчитает и вернет:\n" +
                "   - Общий балл риска\n" +
                "   - Уровень риска\n" +
                "   - Вероятность неблагоприятного исхода\n" +
                "   - Подробную интерпретацию\n" +
                "   - Рекомендации по транспортировке\n\n" +
                "Пример ввода: 7.25, 2, 5, 1800, 4.8, 0, 1 \n\n" +
                "Если вы ознакомились с инструкцией, введите /start, чтобы начать ввод параметров";
        sendResponse(chatId, message);
    }

    private void sendResponse(String chatId, String text) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        execute(message);
    }

    private void sendErrorResponse(String chatId, String text) {
        try {
            sendResponse(chatId, "⚠️ " + text);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private static class UserState {
        private int currentParamIndex = 0;
        private final List<String> parameterValues = new ArrayList<>();
        private final String[] parameterNames = {
                "pH крови",
                "Возраст в часах",
                "Оценка по Апгар",
                "Вес при рождении",
                "PaO2",
                "Врожденные пороки (0 - нет, 1 - да)",
                "Интубация (0 - нет, 1 - да)"
        };

        public String getCurrentParameterName() {
            return parameterNames[currentParamIndex];
        }

        public void addParameterValue(String value) {
            parameterValues.add(value);
            currentParamIndex++;
        }

        public boolean isComplete() {
            return currentParamIndex >= parameterNames.length;
        }

        public void reset() {
            currentParamIndex = 0;
            parameterValues.clear();
        }
    }
}

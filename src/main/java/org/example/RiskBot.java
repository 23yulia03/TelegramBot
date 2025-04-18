package org.example;

import io.github.cdimascio.dotenv.Dotenv;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

public class RiskBot extends TelegramLongPollingBot {
    private final RiskDataStorage storage;
    private final DecimalFormat df = new DecimalFormat("0.00%");

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
            if (text.startsWith("/")) {
                handleCommand(chatId, text);
            } else {
                handleParameters(chatId, text);
            }
        } catch (Exception e) {
            sendErrorResponse(chatId, "Произошла ошибка при обработке запроса: " + e.getMessage());
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

    private void handleParameters(String chatId, String parameters) throws TelegramApiException {
        try {
            List<String> params = Arrays.asList(parameters.split(",\\s*"));

            if (params.size() != 7) {
                sendParameterPrompt(chatId);
                return;
            }

            // Парсинг и валидация параметров
            double pH = parseAndValidate(chatId, params.get(0), "ph");
            int возрастЧасы = (int) parseAndValidate(chatId, params.get(1), "age");
            int апгар = (int) parseAndValidate(chatId, params.get(2), "apgar");
            int вес = (int) parseAndValidate(chatId, params.get(3), "weight");
            double paO2 = parseAndValidate(chatId, params.get(4), "pao2");
            int пороки = parseBinary(chatId, params.get(5), "malformations");
            int интубация = parseBinary(chatId, params.get(6), "intubation");

            // Расчет показателей
            int баллы = calculateHermansenScore(pH, возрастЧасы, апгар, вес, paO2, пороки, интубация);
            double вероятность = calculateMortalityProbability(pH, возрастЧасы, апгар, вес, paO2, пороки, интубация);

            // Формирование ответа
            String результат = buildAssessmentResponse(баллы, вероятность, pH, возрастЧасы, апгар, вес, paO2, пороки, интубация);
            sendResponse(chatId, результат);

        } catch (NumberFormatException e) {
            sendResponse(chatId, "Ошибка формата чисел. Используйте точку для десятичных дробей (например 7.35)");
        } catch (IllegalArgumentException e) {
            // Сообщения валидации уже отправлены
        }
    }

    private double parseAndValidate(String chatId, String значение, String paramName)
            throws TelegramApiException, NumberFormatException, IllegalArgumentException {
        RiskDataStorage.ParameterConfig config = storage.getParameterConfig(paramName);
        try {
            double число = Double.parseDouble(значение);

            // Проверка что значение попадает в какой-либо диапазон
            config.findRange(число);
            return число;

        } catch (NumberFormatException e) {
            sendResponse(chatId, "Некорректное значение для " + config.getDescription() +
                    ". Введите число в формате 7.35");
            throw e;
        } catch (IllegalArgumentException e) {
            sendResponse(chatId, "Значение " + значение + " вне допустимого диапазона для " +
                    config.getDescription());
            throw e;
        }
    }

    private int parseBinary(String chatId, String значение, String paramName)
            throws TelegramApiException, NumberFormatException, IllegalArgumentException {
        int число = (int) parseAndValidate(chatId, значение, paramName);
        if (число != 0 && число != 1) {
            sendResponse(chatId, "Для параметра " + storage.getParameterConfig(paramName).getDescription() +
                    " введите 0 (нет) или 1 (да)");
            throw new IllegalArgumentException();
        }
        return число;
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

        return ответ.toString();
    }

    private String formatParameterDetail(String paramName, double value, String configKey) {
        RiskDataStorage.ParameterConfig config = storage.getParameterConfig(configKey);
        RiskDataStorage.Range range = config.findRange(value);
        return String.format("• %s: %.1f %s - %s (%d баллов)\n",
                paramName, value, config.getUnit(), range.getComment(), range.getScore());
    }

    private void sendParameterPrompt(String chatId) throws TelegramApiException {
        String message = "Пожалуйста, введите все 7 параметров через запятую в следующем порядке:\n\n" +
                "1. Уровень pH крови (например: 7.35)\n" +
                "2. Возраст ребенка в часах (например: 3)\n" +
                "3. Оценка по шкале Апгар на 1-й минуте (0-10)\n" +
                "4. Вес при рождении в граммах (например: 2500)\n" +
                "5. Уровень PaO2 в кПа (например: 5.2)\n" +
                "6. Наличие врожденных пороков (1 - есть, 0 - нет)\n" +
                "7. Интубирован ли ребенок (1 - да, 0 - нет)\n\n" +
                "Пример ввода: 7.25, 2, 5, 1800, 4.8, 0, 1";
        sendResponse(chatId, message);
    }

    private void sendWelcomeMessage(String chatId) throws TelegramApiException {
        String message = "👶 Добро пожаловать в бот оценки риска транспортировки новорожденных!\n\n" +
                "Этот бот рассчитывает риск по шкале Hermansen и вероятность неблагоприятного исхода " +
                "на основании 7 ключевых параметров.\n\n" +
                "Для начала работы введите все параметры через запятую в указанном порядке.\n\n" +
                "Введите /help для подробной инструкции или начните сразу с ввода параметров.";
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
                "Пример ввода: 7.25, 2, 5, 1800, 4.8, 0, 1";
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
}
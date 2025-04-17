package org.example;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class RiskBot extends TelegramLongPollingBot {

    // Замените на чтение из переменных окружения
    final private String BOT_TOKEN = System.getenv("7287403963:AAF6msPxtyU7ZL_1_ayhI7T_lg5EzXRyDPo");
    final private String BOT_NAME = System.getenv("NeoRisk_bot");

    RiskDataStorage storage;

    public RiskBot() {
        storage = new RiskDataStorage();
    }

    @Override
    public String getBotUsername() {
        return BOT_NAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().hasText()) {

                Message inMess = update.getMessage();
                String chatId = inMess.getChatId().toString();
                String userInput = inMess.getText();

                // Обработка команд
                if (userInput.equals("/start")) {
                    sendResponse(chatId, "Добро пожаловать в бот оценки риска транспортировки новорожденных!");
                } else if (userInput.equals("/help")) {
                    sendResponse(chatId, "Введите параметры для расчета риска транспортировки.");
                } else {
                    // Параметры шкал
                    String[] inputs = userInput.split(",");
                    if (inputs.length == 5) {
                        // Ожидаем, что параметры передаются через запятую в формате:
                        // глюкоза, давление, pH, pO2, температура
                        double glucose = Double.parseDouble(inputs[0]);
                        double pressure = Double.parseDouble(inputs[1]);
                        double ph = Double.parseDouble(inputs[2]);
                        double po2 = Double.parseDouble(inputs[3]);
                        double temperature = Double.parseDouble(inputs[4]);

                        // Расчет шкалы Hermansen
                        int hermansenScore = calculateHermansenScore(glucose, pressure, ph, po2, temperature);
                        String hermansenRisk = interpretHermansenRisk(hermansenScore);

                        // Расчет шкалы TRIPS
                        int tripsScore = calculateTripsScore(temperature, pressure);
                        String tripsRisk = interpretTripsRisk(tripsScore);

                        // Отправка ответа с результатами
                        String response = "Риски для транспортировки новорожденного:\n\n";
                        response += "Hermansen Score: " + hermansenScore + " (" + hermansenRisk + ")\n";
                        response += "TRIPS Score: " + tripsScore + " (" + tripsRisk + ")\n";

                        sendResponse(chatId, response);
                    } else {
                        sendResponse(chatId, "Некорректный ввод. Пожалуйста, введите 5 параметров через запятую.");
                    }
                }
            }
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Функция для отправки сообщений
    public void sendResponse(String chatId, String text) throws TelegramApiException {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(text);
        execute(message);
    }

    // Функции для расчета шкалы Hermansen
    public int calculateHermansenScore(double glucose, double pressure, double ph, double po2, double temperature) {
        int score = 0;

        // Глюкоза
        if (glucose < 25) score += 0;
        else if (glucose <= 40) score += 1;
        else if (glucose <= 175) score += 2;
        else score += 1;

        // Систолическое давление
        if (pressure < 30) score += 0;
        else if (pressure <= 39) score += 1;
        else score += 2;

        // pH
        if (ph < 7.20) score += 0;
        else if (ph <= 7.29) score += 1;
        else if (ph <= 7.45) score += 2;
        else if (ph <= 7.50) score += 1;
        else score += 0;

        // pO2
        if (po2 < 40) score += 0;
        else if (po2 <= 49) score += 1;
        else if (po2 <= 100) score += 2;
        else score += 1;

        // Температура
        if (temperature < 36.1) score += 0;
        else if (temperature <= 36.5) score += 1;
        else if (temperature <= 37.2) score += 2;
        else if (temperature <= 37.6) score += 1;
        else score += 0;

        return score;
    }

    // Интерпретация шкалы Hermansen
    public String interpretHermansenRisk(int score) {
        if (score >= 10) return "Низкий риск";
        else if (score >= 8) return "Средний риск";
        else if (score >= 5) return "Высокий риск";
        else return "Экстремально высокий риск";
    }

    // Функции для расчета шкалы TRIPS
    public int calculateTripsScore(double temperature, double pressure) {
        int score = 0;

        // Температура
        if (temperature < 36.1) score += 8;
        else if (temperature <= 36.5) score += 1;
        else if (temperature <= 37.1) score += 0;
        else if (temperature <= 37.6) score += 1;
        else score += 8;

        // Систолическое давление
        if (pressure < 20) score += 26;
        else if (pressure <= 40) score += 16;
        else score += 0;

        return score;
    }

    // Интерпретация шкалы TRIPS
    public String interpretTripsRisk(int score) {
        if (score <= 7) return "Низкий риск";
        else if (score <= 16) return "Умеренный риск";
        else if (score <= 23) return "Высокий риск";
        else return "Очень высокий риск";
    }
}

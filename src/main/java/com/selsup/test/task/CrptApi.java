package com.selsup.test.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Класс предоставляет функционал для взаимодействия с API CRPT для создания документов.
 */
public class CrptApi {
    private static final Logger log = LoggerFactory.getLogger(CrptApi.class);
    private static final String API_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";
    private final AtomicInteger requestsCount = new AtomicInteger(0);
    private final Duration requestInterval;
    private final int requestLimit;
    private final String USER_TOKEN = "SOME_TOKEN";
    private final BlockingQueue<RequestData> requestQueue = new LinkedBlockingQueue<>();
    private final HttpClient client = HttpClient.newHttpClient();

    /**
     * Конструктор класса CrptApi.
     * @param timeUnit     единица времени для интервала запроса
     * @param requestLimit лимит запросов
     */
    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit < 1) {
            throw new IllegalArgumentException("Лимит запросов не может быть меньше единицы");
        }
        this.requestLimit = requestLimit;
        this.requestInterval = Duration.ofSeconds(timeUnit.toSeconds(1));
        new Thread(this::processRequestQueue).start(); // Запуск обработки очереди запросов в отдельном потоке
    }

    /**
     * Метод для создания документа путем отправки запроса к API CRPT.
     * @param document  создаваемый документ
     * @param signature подпись документа
     */
    public void createDocument(Document document, String signature) {
        try {
            log.debug("Добавление запроса на создание документа в очередь");
            requestQueue.put(new RequestData(document, signature)); // Добавление запроса в очередь
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Ошибка при добавлении запроса на создание документа в очередь", e);
        }
    }

    /**
     * Обработка очереди запросов для создания документов.
     */
    private void processRequestQueue() {
        while (true) {
            try {
                RequestData requestData = requestQueue.take(); // Получение запроса из очереди
                if (canMakeRequest()) {
                    makeApiRequest(requestData); // Выполнение запроса к API
                } else {
                    Thread.sleep(requestInterval.toMillis());
                    requestsCount.decrementAndGet();
                    requestQueue.put(requestData); // Повторная постановка запроса в очередь для повторной попытки
                    log.debug("QueueSize = " + requestQueue.size());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Проверка возможности выполнения запроса на основе лимитов запросов.
     * @return true, если запрос может быть выполнен, в противном случае - false
     */
    private synchronized boolean canMakeRequest() {
        return requestsCount.get() < requestLimit;
    }

    /**
     * Выполнение запроса к API CRPT для создания документа.
     * @param requestData данные запроса, содержащие документ и подпись
     */
    private void makeApiRequest(RequestData requestData) {
        try {
            JSONObject requestBody = createRequestBody(requestData);
            log.debug("requestBody = " + requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + USER_TOKEN)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            if (statusCode == 200) {
                log.debug("Документ успешно отправлен: {}", requestData.getDocument().getDoc_id());
            } else {
                log.error("Ошибка при отправке документа. Код ошибки: {}", statusCode);
            }
            log.debug("Увеличение счетчика отправленных запросов");
            requestsCount.incrementAndGet(); // Увеличение счетчика запросов
        } catch (URISyntaxException e) {
            log.error("Ошибка при создании URI", e);
        } catch (IOException e) {
            log.error("Ошибка ввода-вывода", e);
        } catch (InterruptedException e) {
            log.error("Прерывание операции", e);
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Преобразование данных запроса в JSONObject
     * @param requestData данные запроса, содержащие документ и подпись
     * @return JSONObject requestBody, запрос в формате JSON
     */
    private JSONObject createRequestBody(RequestData requestData) {
        JSONObject document = new JSONObject(requestData.document);
        JSONObject requestBody = new JSONObject();
        requestBody.put("document", document);
        requestBody.put("signature", requestData.signature);
        return requestBody;
    }

    /**
     * Внутренний класс для хранения данных запроса.
     */
    @Getter
    @AllArgsConstructor
    static class RequestData {
        private final Document document;
        private final String signature;
    }

    /**
     * Внутренний класс, представляющий документ.
     */
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Document {
        private Description description;

        private String doc_id;

        private String doc_status;

        private String doc_type;

        private Boolean importRequest;

        private String owner_inn;

        private String participant_inn;

        private String producer_inn;

        private LocalDate production_date;

        private String production_type;

        private List<Product> products;

        private LocalDate reg_date;

        private String reg_number;
    }

    /**
     * Внутренний класс, представляющий продукт.
     */
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Product {
        private String certificate_document;

        private LocalDate certificate_document_date;

        private String certificate_document_number;

        private String owner_inn;

        private String producer_inn;

        private LocalDate production_date;

        private String tnved_code;

        private String uit_code;

        private String uitu_code;
    }

    /**
     * Внутренний класс, представляющий описание документа.
     */
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Description {
        private String participantInn;
    }
}

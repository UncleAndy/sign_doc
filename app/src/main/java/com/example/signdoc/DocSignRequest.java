package com.example.signdoc;

/**
 * Created by andy on 12.12.14.
 */
public class DocSignRequest extends Doc {
    public String type;
    public String site;
    public String id;       // Внутренний (клиента) идентификатор документа
    public String data;     // Данные - это зашифрованный JSON массив строк
    public String template; // Формат: До первого перевода строки - код типа шаблона:
                            // LIST - список строк с описанием каждого значения документа (по порядку, построчно)
}

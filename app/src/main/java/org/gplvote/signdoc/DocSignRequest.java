package org.gplvote.signdoc;

public class DocSignRequest extends Doc {
    public String type = "SIGN_REQUEST";
    public String site;
    public String doc_id;       // Внутренний (клиента) идентификатор документа
    public String user_key_id;  // Идентификатор открытого ключа пользователя
    public String data;     // Данные - это зашифрованный JSON массив строк
    public String dec_data; // Расшифрованные данные в строковом виде. Используются в коде, через сеть не передаются
    public String template; // Формат: До первого перевода строки - код типа шаблона:
                            // LIST - список строк с описанием каждого значения документа (по порядку, построчно)
                            // HTML - html шаблон со значаениями вставляеммыми на место <%data_N%> (N - номер элемента в массиве данных начиная с 0)
    public String sign_url; // Если заполнено, то подпись нужно отправлять по данному URL POST запросом
}

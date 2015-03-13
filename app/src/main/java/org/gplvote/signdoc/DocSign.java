package org.gplvote.signdoc;

public class DocSign extends Doc {
    public String type = "SIGN";
    public String site;
    public String doc_id;   // Внутренний (клиента) идентификатор документа
    public String sign;     // Подпись в Base64 для sha256(данные+шаблон)
    public String sign_type = Sign.SIGN_ALG_TAG; // Тип подписи ("SHA1withRSA", "SHA256withRSA", ...)
    public String public_key; // Публичный ключ пользователя при подписании публичных документов
}

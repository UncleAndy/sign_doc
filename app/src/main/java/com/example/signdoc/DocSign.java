package com.example.signdoc;

/**
 * Created by andy on 13.12.14.
 */
public class DocSign {
    public String type = "SIGN";
    public String site;
    public String id;       // Внутренний (клиента) идентификатор документа
    public String sign;     // Подпись в Base64 для sha256(данные+шаблон)
}

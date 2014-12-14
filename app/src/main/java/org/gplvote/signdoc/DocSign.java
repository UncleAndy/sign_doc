package org.gplvote.signdoc;

/**
 * Created by andy on 13.12.14.
 */
public class DocSign extends Doc {
    public String type = "SIGN";
    public String site;
    public String doc_id;   // Внутренний (клиента) идентификатор документа
    public String sign;     // Подпись в Base64 для sha256(данные+шаблон)
}

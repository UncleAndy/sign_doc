package com.example.signdoc;

/**
 * Created by andy on 12.12.14.
 */
public class DocSignRequest extends Doc {
    public String type;
    public String site;
    public String data; // Данные - это зашифрованный JSON массив строк
    public String template;
}

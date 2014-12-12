package com.example.signdoc;

import com.google.gson.Gson;

/**
 * Created by andy on 12.12.14.
 */
public class DocSignRegistration extends Doc {
    public String type = "REGISTER";
    public String site;
    public String code;
    public String public_key;
    public String sign;
}

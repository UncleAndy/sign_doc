package com.example.signdoc;

/**
 * Created by andy on 12.12.14.
 */
public class DocRequestForUser extends Doc {
    public String type = "USER_REQUEST";
    public String public_key_id;
    public Long from_time;   // Unix time
    public String public_key;
    public String sign;
}

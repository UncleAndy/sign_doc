package org.gplvote.signdoc;

public class DocSignRegistration extends Doc {
    public String type = "REGISTER";
    public String site;
    public String code;
    public String public_key;
    public String sign;
    public String sign_type = Sign.SIGN_ALG_TAG;
    public String cancel_public_key;
    public String cancel_public_key_sign;
}

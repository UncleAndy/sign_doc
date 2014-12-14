package org.gplvote.signdoc;

import com.google.gson.Gson;

/**
 * Created by andy on 12.12.14.
 */
public class Doc {
    public String toJson() {
        Gson gson = new Gson();
        String json = gson.toJson(this);
        return(json);
    };
}

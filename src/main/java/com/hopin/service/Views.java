package com.hopin.service;

import com.hopin.model.User;
import java.util.LinkedHashMap;
import java.util.Map;

public class Views {

    public static Map<String, Object> self(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id);
        m.put("name", u.name);
        m.put("email", u.email);
        m.put("role", u.role);
        m.put("rating", u.rating);
        m.put("ratingCount", u.ratingCount);
        m.put("blocked", u.blocked);
        m.put("noSmoking", u.noSmoking);
        m.put("noPets", u.noPets);
        m.put("ac", u.ac);
        m.put("music", u.music);
        m.put("luggage", u.luggage);
        m.put("note", u.note);
        m.put("phone", u.phone);
        m.put("emailVerified", Boolean.TRUE.equals(u.emailVerified));
        m.put("phoneVerified", Boolean.TRUE.equals(u.phoneVerified));
        m.put("idType", u.idType);
        m.put("idLast4", u.idLast4);
        m.put("idStatus", u.idStatus == null ? "NONE" : u.idStatus);
        m.put("idVerified", u.idVerified());
        m.put("verified", u.verified());
        m.put("hasPhoto", Boolean.TRUE.equals(u.hasPhoto));
        m.put("photoVersion", u.photoVersion == null ? 0 : u.photoVersion);
        return m;
    }
}

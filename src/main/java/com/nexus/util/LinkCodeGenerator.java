package com.nexus.util;

import java.security.SecureRandom;

public class LinkCodeGenerator {
    
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private static final SecureRandom random = new SecureRandom();
    
    /**
     * Genera un código de enlace único en formato XXXX-XXXX
     * Ejemplo: AB7K-M9P2
     */
    public static String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH + 1);
        
        for (int i = 0; i < CODE_LENGTH; i++) {
            if (i == 4) {
                code.append('-');
            }
            int index = random.nextInt(CHARACTERS.length());
            code.append(CHARACTERS.charAt(index));
        }
        
        return code.toString();
    }
    
    /**
     * Valida el formato del código de enlace
     */
    public static boolean isValidFormat(String code) {
        if (code == null || code.length() != 9) {
            return false;
        }
        
        String pattern = "^[A-Z0-9]{4}-[A-Z0-9]{4}$";
        return code.matches(pattern);
    }
}

package com.nexus.entity;

import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceId implements Serializable {
    private Long user;
    private Long preference;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserPreferenceId that = (UserPreferenceId) o;
        return Objects.equals(user, that.user) && Objects.equals(preference, that.preference);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(user, preference);
    }
}

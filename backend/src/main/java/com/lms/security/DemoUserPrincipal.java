package com.lms.security;

import com.lms.module.user.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapter between our {@link User} entity and Spring Security's {@link UserDetails}.
 * Used by {@link DemoUserDetailsService} to authenticate users against the auth server login form.
 */
@Getter
public class DemoUserPrincipal implements UserDetails {

    private final String username;   // email address (login field)
    private final String password;   // BCrypt-encoded password
    private final String email;
    private final String firstName;
    private final String lastName;
    private final User.UserRole role;
    private final boolean active;

    public DemoUserPrincipal(String email, String encodedPassword,
                              String firstName, String lastName,
                              User.UserRole role, boolean active) {
        this.username  = email;
        this.password  = encodedPassword;
        this.email     = email;
        this.firstName = firstName;
        this.lastName  = lastName;
        this.role      = role;
        this.active    = active;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword()             { return password; }
    @Override public String getUsername()             { return username; }
    @Override public boolean isAccountNonExpired()   { return active; }
    @Override public boolean isAccountNonLocked()    { return active; }
    @Override public boolean isCredentialsNonExpired(){ return true; }
    @Override public boolean isEnabled()             { return active; }
}

package com.rutaexpress.bff.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                defaultAuthoritiesConverter.convert(jwt).stream(),
                extractAzureRoles(jwt).stream()
        ).collect(Collectors.toSet());

        String principalClaimName = jwt.getClaimAsString("preferred_username");
        if (principalClaimName == null) {
            principalClaimName = jwt.getClaimAsString("upn");
        }
        if (principalClaimName == null) {
            principalClaimName = jwt.getSubject();
        }

        return new JwtAuthenticationToken(jwt, authorities, principalClaimName);
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractAzureRoles(Jwt jwt) {
        Object rolesClaim = jwt.getClaims().get("roles");
        if (rolesClaim instanceof List<?> roleList) {
            Set<GrantedAuthority> authorities = new HashSet<>();
            for (Object role : roleList) {
                if (role != null) {
                    String roleName = role.toString().trim();
                    if (!roleName.isEmpty()) {
                        // Rol exacto como viene de Azure (ej: Admin, Despachador, Cliente, Auditor)
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + roleName));
                        // Tolerancia a variaciones de mayúsculas/minúsculas (ej: admin -> Admin)
                        String capitalized = Character.toUpperCase(roleName.charAt(0)) + roleName.substring(1).toLowerCase();
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + capitalized));
                    }
                }
            }
            return authorities;
        }
        return Collections.emptyList();
    }
}
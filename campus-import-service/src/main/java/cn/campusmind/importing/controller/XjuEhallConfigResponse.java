package cn.campusmind.importing.controller;

import java.util.List;

public record XjuEhallConfigResponse(
        boolean enabled,
        String loginUrl,
        List<String> allowedAuthHosts,
        List<String> allowedDataHosts,
        List<String> supportedScopes,
        int schemaVersion,
        String policyVersion,
        int maxPayloadBytes,
        int maxSourceItems
) { }

package com.scanales.homedir.cfp;

import java.util.List;

public record CfpFormCatalog(
    List<CfpFormOption> levels,
    List<CfpFormOption> formats,
    List<CfpFormOption> durations,
    List<CfpFormOption> languages,
    List<CfpFormOption> tracks) {
}
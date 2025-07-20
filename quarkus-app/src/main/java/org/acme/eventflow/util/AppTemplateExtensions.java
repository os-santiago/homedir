package org.acme.eventflow.util;

import java.time.LocalDate;

import io.quarkus.qute.TemplateExtension;

@TemplateExtension(namespace = "app")
public class AppTemplateExtensions {
    public static int currentYear() {
        return LocalDate.now().getYear();
    }
}

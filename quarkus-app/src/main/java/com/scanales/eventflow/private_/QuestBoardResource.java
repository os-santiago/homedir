package com.scanales.eventflow.private_;

import com.scanales.eventflow.model.Quest;
import com.scanales.eventflow.service.QuestService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/quests")
@Authenticated
public class QuestBoardResource {

    @Inject
    QuestService questService;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance quests(List<Quest> quests);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance list() {
        List<Quest> quests = questService.getQuestBoard();
        return Templates.quests(quests);
    }
}

package com.scanales.eventflow.service;

import com.scanales.eventflow.model.CharacterProfile;
import com.scanales.eventflow.model.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CharacterService {

    @Inject
    UserProfileService userProfileService;

    public CharacterProfile getCharacter(String email) {
        if (email == null) {
            return CharacterProfile.visitor();
        }

        var profileOpt = userProfileService.find(email);
        if (profileOpt.isEmpty()) {
            return CharacterProfile.novice();
        }

        UserProfile p = profileOpt.get();
        var gh = p.getGithub();

        if (gh == null) {
            return CharacterProfile.novice();
        }

        // Gamification Logic (Mock/Simple)
        // In a real scenario, we would fetch contribs from a GithubService or DB
        int contribs = 0; // Fetch real count
        int events = 0; // Fetch from UserScheduleService?

        // For demo purposes, we can simulate some XP based on if they are linked
        int baseXp = 50;
        contribs = 5; // Simulating some history

        int xp = baseXp + (contribs * 10);
        int level = 1 + (xp / 100);

        return new CharacterProfile(
                "CONTRIBUTOR",
                level,
                xp % 100, // Current XP in level
                100, // Next level at 100 (simplified)
                100, 100, // Full HP
                80, 100, // SP
                contribs,
                0, // quests
                events,
                0, // projects
                0 // network
        );
    }
}

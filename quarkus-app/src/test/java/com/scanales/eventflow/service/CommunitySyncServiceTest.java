package com.scanales.eventflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.scanales.eventflow.model.CommunityMember;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public class CommunitySyncServiceTest {

    @Test
    public void testSerialization() throws Exception {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        yamlMapper.findAndRegisterModules();

        CommunityMember member = new CommunityMember();
        member.setUserId("user1");
        member.setDisplayName("Test User");
        member.setGithub("testuser");
        member.setJoinedAt(Instant.now());

        CommunityData data = new CommunityData(List.of(member));

        String yaml = yamlMapper.writeValueAsString(data);
        System.out.println(yaml);
    }

    private static class CommunityData {
        public List<CommunityMember> members;

        public CommunityData(List<CommunityMember> members) {
            this.members = members;
        }
    }
}

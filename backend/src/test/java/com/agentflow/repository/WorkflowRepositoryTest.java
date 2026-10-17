package com.agentflow.repository;

import com.agentflow.entity.Workflow;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class WorkflowRepositoryTest {

    @Autowired
    private WorkflowRepository workflowRepository;

    @Test
    @DisplayName("Persist and query workflow with JSON workflow_definition")
    void shouldPersistAndFindWorkflow() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode graph = mapper.createObjectNode();
        graph.put("nodesCount", 5);

        Workflow workflow = new Workflow("Integration Workflow", "Tests JPA persistence", graph, true);
        Workflow saved = workflowRepository.save(workflow);

        assertThat(saved.getId()).isNotNull();

        Optional<Workflow> found = workflowRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Integration Workflow");
        assertThat(found.get().getWorkflowDefinition().get("nodesCount").asInt()).isEqualTo(5);
        assertThat(found.get().isActive()).isTrue();

        List<Workflow> activeList = workflowRepository.findByActive(true);
        assertThat(activeList).hasSize(1);
    }
}

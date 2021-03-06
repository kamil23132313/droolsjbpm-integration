/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kie.karaf.itest.kieserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.core.command.runtime.BatchExecutionCommandImpl;
import org.drools.core.command.runtime.rule.FireAllRulesCommand;
import org.drools.core.command.runtime.rule.InsertObjectCommand;
import org.kie.api.command.ExecutableCommand;
import org.kie.karaf.itest.AbstractKarafIntegrationTest;
import org.kie.server.api.marshalling.MarshallingFormat;
import org.kie.server.api.model.KieContainerResource;
import org.kie.server.api.model.KieContainerResourceList;
import org.kie.server.api.model.ServiceResponse;
import org.kie.server.api.model.definition.ProcessDefinition;
import org.kie.server.api.model.instance.ProcessInstance;
import org.kie.server.api.model.instance.TaskSummary;
import org.kie.server.client.KieServicesClient;
import org.kie.server.client.KieServicesConfiguration;
import org.kie.server.client.KieServicesFactory;
import org.kie.server.client.ProcessServicesClient;
import org.kie.server.client.QueryServicesClient;
import org.kie.server.client.RuleServicesClient;
import org.kie.server.client.UserTaskServicesClient;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.LogLevelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.configureConsole;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.logLevel;

/**
 * These tests aims at verifying if KieServerClient can run on Karaf.
 * By default they are disabled (ignored) as they require KieServer to be up and running so client can connect to it.
 * To be able to use it following constants must be given (either directly or as system properties)
 * - SERVER_URL (-Dorg.kie.server.itest.server.url)
 * - USER (-Dorg.kie.server.itest.user)
 * - PASSWORD (-Dorg.kie.server.itest.password)
 * - CONTAINER_ID (-Dorg.kie.server.itest.container)
 * - PROCESS_ID (-Dorg.kie.server.itest.process)
 *
 * Tests are very basic and focus only on verification rather than complete test suite.
 */

public class BaseKieServerClientKarafIntegrationTest extends AbstractKarafIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(BaseKieServerClientKarafIntegrationTest.class);

    protected String serverUrl = System.getProperty("org.kie.server.itest.server.url", "http://localhost:8080/kie-server/services/rest/server");

    protected void testListContainers(MarshallingFormat marshallingFormat) {
        KieServicesConfiguration configuration = KieServicesFactory.newRestConfiguration(serverUrl,
                                                                                         KieServerConstants.user,
                                                                                         KieServerConstants.password);

        configuration.setMarshallingFormat(marshallingFormat);
//        configuration.addJaxbClasses(extraClasses);
//        KieServicesClient kieServicesClient =  KieServicesFactory.newKieServicesClient(configuration, kieContainer.getClassLoader());
        KieServicesClient kieServicesClient =  KieServicesFactory.newKieServicesClient(configuration, this.getClass().getClassLoader());

        ServiceResponse<KieContainerResourceList> containersResponse = kieServicesClient.listContainers();
        assertNotNull(containersResponse);
        assertEquals(ServiceResponse.ResponseType.SUCCESS, containersResponse.getType());
        assertNotNull(containersResponse.getResult());

        List<KieContainerResource> containers = containersResponse.getResult().getContainers();
        assertNotNull(containers);
        logger.info("Found containers = " + containers);
        // change the assert according to actual state of kie server
        assertTrue(containers.size() > 0);
    }


    protected void testCompleteInteractionWithKieServer(MarshallingFormat marshallingFormat) {
        KieServicesConfiguration configuration = KieServicesFactory.newRestConfiguration(serverUrl,
                                                                                         KieServerConstants.user,
                                                                                         KieServerConstants.password);

        configuration.setMarshallingFormat(marshallingFormat);
//        configuration.addJaxbClasses(extraClasses);
//        KieServicesClient kieServicesClient =  KieServicesFactory.newKieServicesClient(configuration, kieContainer.getClassLoader());
        KieServicesClient kieServicesClient =  KieServicesFactory.newKieServicesClient(configuration, this.getClass().getClassLoader());

        // query for all available process definitions
        QueryServicesClient queryClient = kieServicesClient.getServicesClient(QueryServicesClient.class);
        List<ProcessDefinition> processes = queryClient.findProcesses(0, 10);
        System.out.println("\t######### Available processes" + processes);

        ProcessServicesClient processClient = kieServicesClient.getServicesClient(ProcessServicesClient.class);
        // get details of process definition
        ProcessDefinition definition = processClient.getProcessDefinition(KieServerConstants.containerId,
                                                                          KieServerConstants.processId);
        System.out.println("\t######### Definition details: " + definition);

        // start process instance
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("employee", KieServerConstants.user);
        Long processInstanceId = processClient.startProcess(KieServerConstants.containerId,
                                                            KieServerConstants.processId, params);
        System.out.println("\t######### Process instance id: " + processInstanceId);

        UserTaskServicesClient taskClient = kieServicesClient.getServicesClient(UserTaskServicesClient.class);
        // find available tasks
        List<TaskSummary> tasks = taskClient.findTasks(KieServerConstants.user, 0, 10);
        System.out.println("\t######### Tasks: " +tasks);

        // complete task
        Long taskId = tasks.get(0).getId();

        taskClient.startTask(KieServerConstants.containerId, taskId, KieServerConstants.user);
        taskClient.completeTask(KieServerConstants.containerId, taskId, KieServerConstants.user, null);

        // work with rules
        List<ExecutableCommand<?>> commands = new ArrayList<ExecutableCommand<?>>();
        BatchExecutionCommandImpl executionCommand = new BatchExecutionCommandImpl(commands);
        executionCommand.setLookup("defaultKieSession");

        InsertObjectCommand insertObjectCommand = new InsertObjectCommand();
        insertObjectCommand.setOutIdentifier("person");
        insertObjectCommand.setObject("john");

        FireAllRulesCommand fireAllRulesCommand = new FireAllRulesCommand();

        commands.add(insertObjectCommand);
        commands.add(fireAllRulesCommand);

        RuleServicesClient ruleClient = kieServicesClient.getServicesClient(RuleServicesClient.class);
        ruleClient.executeCommands(KieServerConstants.containerId, executionCommand);
        System.out.println("\t######### Rules executed");

        // at the end abort process instance
        processClient.abortProcessInstance(KieServerConstants.containerId, processInstanceId);

        ProcessInstance processInstance = queryClient.findProcessInstanceById(processInstanceId);
        System.out.println("\t######### ProcessInstance: " + processInstance);
    }

    @Configuration
    public static Option[] configure() {

        return new Option[]{
                // Install Karaf Container
                getKarafDistributionOption(),

                // Don't bother with local console output as it just ends up cluttering the logs
                configureConsole().ignoreLocalConsole(),
                // Force the log level to INFO so we have more details during the test.  It defaults to WARN.
                logLevel(LogLevelOption.LogLevel.WARN),

                // Option to be used to do remote debugging
//                  debugConfiguration("5005", true),

                // Load kie-server-client
                loadKieFeatures("kie-server-client")
        };
    }


}
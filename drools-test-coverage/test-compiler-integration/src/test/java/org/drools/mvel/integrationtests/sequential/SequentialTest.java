/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.drools.mvel.integrationtests.sequential;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.drools.compiler.compiler.DroolsParserException;
import org.drools.kiesession.rulebase.InternalKnowledgeBase;
import org.drools.mvel.compiler.Cheese;
import org.drools.mvel.compiler.Message;
import org.drools.mvel.compiler.Person;
import org.drools.mvel.integrationtests.phreak.A;
import org.drools.mvel.integrationtests.DynamicRulesTest;
import org.drools.testcoverage.common.util.KieBaseTestConfiguration;
import org.drools.testcoverage.common.util.KieBaseUtil;
import org.drools.testcoverage.common.util.KieUtil;
import org.drools.testcoverage.common.util.TestParametersUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.builder.KieModule;
import org.kie.api.conf.SequentialOption;
import org.kie.api.definition.KiePackage;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.AgendaGroupPoppedEvent;
import org.kie.api.event.rule.AgendaGroupPushedEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.MatchCancelledEvent;
import org.kie.api.event.rule.MatchCreatedEvent;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleFlowGroupActivatedEvent;
import org.kie.api.event.rule.RuleFlowGroupDeactivatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.StatelessKieSession;
import org.kie.internal.command.CommandFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class SequentialTest {

    private final KieBaseTestConfiguration kieBaseTestConfiguration;

    public SequentialTest(final KieBaseTestConfiguration kieBaseTestConfiguration) {
        this.kieBaseTestConfiguration = kieBaseTestConfiguration;
    }

    @Parameterized.Parameters(name = "KieBase type={0}")
    public static Collection<Object[]> getParameters() {
        return TestParametersUtil.getKieBaseCloudConfigurations(true);
    }

    @Test
    public void testSequentialPlusPhreakOperationComplex() throws Exception {
        String str = "";
        str += "package org.drools.mvel.compiler.test\n";
        str +="import " + A.class.getCanonicalName() + "\n";
        str +="global  " + List.class.getCanonicalName() + " list\n";


        // Focus is done as g1, g2, g1 to demonstrate that groups will not re-activate
        str +="rule r0 when\n";
        str +="then\n";
        str +="    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( 'g1' ).setFocus();\n";
        str +="    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( 'g2' ).setFocus();\n";
        str +="    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( 'g1' ).setFocus();\n";
        str +="end\n";
        str +="rule r1 agenda-group 'g1' when\n";
        str +="    a : A( object > 0 )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="    modify(a) { setObject( 3 ) };\n";
        str +="end\n";

        // r1_x is here to show they do not react when g2.r9 changes A o=2,
        // i.e. checking that re-activating g1 won't cause it to pick up previous non evaluated rules.
        // this is mostly checking that the no linking doesn't interfere with the expected results.
        str +="rule r1_x agenda-group 'g1' when\n";
        str +="    a : A( object == 2 )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        // r1_y is here to show it does not react when A is changed to o=5 in r3
        str +="rule r1_y agenda-group 'g1' when\n";
        str +="    a : A( object == 5 )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        str +="rule r2 agenda-group 'g1' when\n";
        str +="    a : A( object < 3 )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";
        str +="rule r3 agenda-group 'g1' when\n";
        str +="    a : A(object >= 3  )\n";
        str +="then\n";
        str +="    modify(a) { setObject( 5 ) };\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        // Checks that itself, f3 and r1_y do not react as they are higher up
        str +="rule r4 agenda-group 'g1' when\n";
        str +="    a : A(object >= 2  )\n";
        str +="then\n";
        str +="    modify(a) { setObject( 5 ) };\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        // Checks that while this at one point matches, it does not match by the time g2 is entered
        // nor does it react when r9 changes a o=2
        str +="rule r6 agenda-group 'g2' when\n";
        str +="    a : A(object < 5  )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        str +="rule r7 agenda-group 'g2' when\n";
        str +="    a : A(object >= 3  )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";
        str +="rule r8 agenda-group 'g2' when\n";
        str +="    a : A(object >= 5  )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        // This changes A o=2 to check if g1.r1_x incorrect reacts when g1 is re-entered
        str +="rule r9 agenda-group 'g2' when\n";
        str +="    a : A(object >= 5  )\n";
        str +="then\n";
        str +="    modify(a) { setObject( 2 ) };\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, str);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        final List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );

        ksession.execute( CommandFactory.newInsertElements(Arrays.asList( new Object[]{new A(1)} )) );

        assertEquals( 6, list.size() );
        assertEquals( "r1", list.get(0));
        assertEquals( "r3", list.get(1));
        assertEquals( "r4", list.get(2));
        assertEquals( "r7", list.get(3));
        assertEquals( "r8", list.get(4));
        assertEquals( "r9", list.get(5));
    }

    @Test
    public void testSequentialPlusPhreakRevisitOriginallyEmptyGroup() throws Exception {
        String str = "";
        str += "package org.drools.mvel.compiler.test\n";
        str +="import " + A.class.getCanonicalName() + "\n";
        str +="global  " + List.class.getCanonicalName() + " list\n";

        // Focus is done as g1, g2, g1 to demonstrate that groups will not re-activate
        str +="rule r0 when\n";
        str +="then\n";
        str +="    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( 'g1' ).setFocus();\n";
        str +="    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( 'g2' ).setFocus();\n";
        str +="    drools.getKnowledgeRuntime().getAgenda().getAgendaGroup( 'g1' ).setFocus();\n";
        str +="end\n";

        // r1_x is here to show they do not react when g2.r9 changes A o=2,
        // i.e. checking that re-activating g1 won't cause it to pick up previous non evaluated rules.
        // this is mostly checking that the none linking doesn't interfere with the expected results.
        // additional checks this works if g1 never had any Matches on the first visit
        str +="rule r1_x agenda-group 'g1' when\n";
        str +="    a : A( object == 2 )\n";
        str +="then\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        // This changes A o=2 to check if g1.r1_x incorrect reacts when g1 is re-entered
        str +="rule r9 agenda-group 'g2' when\n";
        str +="    a : A(object >= 5  )\n";
        str +="then\n";
        str +="    modify(a) { setObject( 2 ) };\n";
        str +="    list.add( drools.getRule().getName() );\n";
        str +="end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, str);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();
        final List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );

        ksession.execute( CommandFactory.newInsertElements(Arrays.asList( new Object[]{new A(5)} )) );

        assertEquals( 1, list.size() );
        assertEquals( "r9", list.get(0));
    }

    @Test
    public void testBasicOperation() throws Exception {
        final KieModule kieModule = KieUtil.getKieModuleFromClasspathResources("test", getClass(), kieBaseTestConfiguration, "simpleSequential.drl");
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();
        final List list = new ArrayList();
        ksession.setGlobal( "list",
                           list );

        final Person p1 = new Person( "p1",
                                      "stilton" );
        final Person p2 = new Person( "p2",
                                      "cheddar" );
        final Person p3 = new Person( "p3",
                                      "stilton" );

        final Cheese stilton = new Cheese( "stilton",
                                           15 );
        final Cheese cheddar = new Cheese( "cheddar",
                                           15 );


        ksession.execute( CommandFactory.newInsertElements( Arrays.asList( new Object[]{p1, stilton, p2, cheddar, p3} ) ) );

        assertEquals( 3,
                      list.size() );
    }

    @Test
    public void testSalience() throws Exception {
        final KieModule kieModule = KieUtil.getKieModuleFromClasspathResources("test", getClass(), kieBaseTestConfiguration, "simpleSalience.drl");
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        final List list = new ArrayList();
        ksession.setGlobal( "list",
                           list );

        ksession.execute( new Person( "pob")  );

        assertEquals( 3,
                      list.size() );

        assertEquals( "rule 3", list.get( 0 ));
        assertEquals( "rule 2", list.get( 1 ) );
        assertEquals( "rule 1", list.get( 2 ) );
    }

    @Test
    public void testKnowledgeRuntimeAccess() throws Exception {
        String str = "";
        str += "package org.drools.mvel.compiler.test\n";
        str +="import org.drools.mvel.compiler.Message\n";
        str +="rule \"Hello World\"\n";
        str +="when\n";
        str +="    Message( )\n";
        str +="then\n";
        str +="    System.out.println( drools.getKieRuntime() );\n";
        str +="end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, str);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        ksession.execute( new Message( "help" ) );
    }

    @Test
    public void testEvents() throws Exception {
        String str = "";
        str += "package org.drools.mvel.compiler.test\n";
        str +="import org.drools.mvel.compiler.Message\n";
        str +="rule \"Hello World\"\n";
        str +="when\n";
        str +="    Message( )\n";
        str +="then\n";
        str +="    System.out.println( drools.getKieRuntime() );\n";
        str +="end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, str);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        final List list = new ArrayList();

        ksession.addEventListener( new AgendaEventListener() {

            public void matchCancelled( MatchCancelledEvent event ) {
                assertNotNull( event.getKieRuntime() );
                list.add( event );
            }

            public void matchCreated( MatchCreatedEvent event ) {
                assertNotNull( event.getKieRuntime() );
                list.add( event );
            }

            public void afterMatchFired( AfterMatchFiredEvent event ) {
                assertNotNull( event.getKieRuntime() );
                list.add( event );
            }

            public void agendaGroupPopped( AgendaGroupPoppedEvent event ) {
                assertNotNull( event.getKieRuntime() );
                list.add( event );
            }

            public void agendaGroupPushed( AgendaGroupPushedEvent event ) {
                assertNotNull( event.getKieRuntime() );
                list.add( event );
            }

            public void beforeMatchFired( BeforeMatchFiredEvent event ) {
                assertNotNull( event.getKieRuntime() );
                list.add( event );
            }

            public void beforeRuleFlowGroupActivated( RuleFlowGroupActivatedEvent event ) {
                assertNotNull( event.getKieRuntime() );
                list.add( event );
            }

            public void afterRuleFlowGroupActivated( RuleFlowGroupActivatedEvent event ) {
                assertNotNull( event.getKieRuntime() );
                list.add( event );
            }

            public void beforeRuleFlowGroupDeactivated( RuleFlowGroupDeactivatedEvent event ) {
                assertNotNull( event.getKieRuntime() );
                list.add( event );
            }

            public void afterRuleFlowGroupDeactivated( RuleFlowGroupDeactivatedEvent event ) {
                assertNotNull( event.getKieRuntime() );
                list.add( event );
            }

        } );

        ksession.addEventListener( new RuleRuntimeEventListener() {

            public void objectInserted( ObjectInsertedEvent event ) {
                assertNotNull( event.getKieRuntime() );
                list.add( event );
            }

            public void objectDeleted( ObjectDeletedEvent event ) {
                assertNotNull( event.getKieRuntime() );
                list.add( event );
            }

            public void objectUpdated( ObjectUpdatedEvent event ) {
                assertNotNull( event.getKieRuntime() );
                list.add( event );
            }

        } );

        ksession.execute( new Message( "help" ) );

        assertEquals( 4, list.size() );
    }


    // JBRULES-1567 - ArrayIndexOutOfBoundsException in sequential execution after calling RuleBase.addPackage(..)
    @Test
    public void testSequentialWithRulebaseUpdate() throws Exception {
        final KieModule kieModule = KieUtil.getKieModuleFromClasspathResources("test", getClass(), kieBaseTestConfiguration, "simpleSalience.drl");
        final InternalKnowledgeBase kbase = (InternalKnowledgeBase) KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        final List list = new ArrayList();
        ksession.setGlobal( "list", list );

        ksession.execute(new Person("pob"));

        Collection<KiePackage> kpkgs = KieBaseUtil.getKieBaseFromClasspathResources("tmp", DynamicRulesTest.class, kieBaseTestConfiguration, "test_Dynamic3.drl").getKiePackages();
        kbase.addPackages(kpkgs);

        ksession = kbase.newStatelessKieSession();
        ksession.setGlobal( "list", list );
        Person person  = new Person("bop");
        ksession.execute(person);

        assertEquals( 7, list.size() );

        assertEquals( "rule 3", list.get( 0 ));
        assertEquals( "rule 2", list.get( 1 ));
        assertEquals( "rule 1", list.get( 2 ));
        assertEquals( "rule 3", list.get( 3 ) );
        assertEquals( "rule 2", list.get( 4 ) );
        assertEquals( "rule 1", list.get( 5 ) );
        assertEquals( person, list.get( 6 ));
    }

    @Test
    public void testProfileSequential() throws Exception {

        runTestProfileManyRulesAndFacts( true, "Sequential mode", 0, "sequentialProfile.drl"  );
        runTestProfileManyRulesAndFacts( true, "Sequential mode", 0, "sequentialProfile.drl"  );

        System.gc();
        Thread.sleep( 100 );
    }

    @Test
    public void testProfileRETE() throws Exception {
        runTestProfileManyRulesAndFacts( false, "Normal RETE mode", 0, "sequentialProfile.drl"  );
        runTestProfileManyRulesAndFacts( false, "Normal RETE mode", 0, "sequentialProfile.drl"  );

        System.gc();
        Thread.sleep( 100 );
    }

    @Test
    public void testNumberofIterationsSeq() throws Exception {
        //test throughput
        runTestProfileManyRulesAndFacts( true,
                                         "SEQUENTIAL",
                                         2000, "sequentialProfile.drl" );
    }

    @Test
    public void testNumberofIterationsRETE() throws Exception {
        //test throughput
        runTestProfileManyRulesAndFacts( false,
                                         "RETE",
                                         2000, "sequentialProfile.drl"  );

    }

    @Test
    public void testPerfJDT() throws Exception {
        runTestProfileManyRulesAndFacts( true,
                                         "JDT",
                                         2000, "sequentialProfile.drl"  );

    }

    @Test
    public void testPerfMVEL() throws Exception {
        runTestProfileManyRulesAndFacts( true,
                                         "MVEL",
                                         2000, "sequentialProfileMVEL.drl"  );

    }


    private void runTestProfileManyRulesAndFacts(boolean sequentialMode,
                                                 String message,
                                                 int timetoMeasureIterations,
                                                 String file) throws DroolsParserException, IOException, Exception {
        SequentialOption opt;
        if ( sequentialMode ) {
            opt = SequentialOption.YES;
        }   else {
            opt = SequentialOption.NO;
        }
        final KieModule kieModule = KieUtil.getKieModuleFromClasspathResources("test", getClass(), kieBaseTestConfiguration, file);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, opt);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        final List list = new ArrayList();
        ksession.setGlobal( "list",
                           list );

        Object[] data = new Object[50000];
        for ( int i = 0; i < data.length; i++ ) {

            if ( i % 2 == 0 ) {
                final Person p = new Person( "p" + i,
                                             "stilton" );
                data[i] = p;
            } else {
                data[i] = new Cheese( "cheddar",
                                      i );
            }
        }

        if ( timetoMeasureIterations == 0 ) {
            //one shot measure
            long start = System.currentTimeMillis();
            ksession.execute( CommandFactory.newInsertElements(Arrays.asList(data)));
            System.out.println( "Time for " + message + ":" + (System.currentTimeMillis() - start) );
            assertTrue( list.size() > 0 );

        } else {
            //lots of shots
            //test throughput
            long start = System.currentTimeMillis();
            long end = start + timetoMeasureIterations;
            int count = 0;
            while ( System.currentTimeMillis() < end ) {
                StatelessKieSession sess2 = kbase.newStatelessKieSession();
                List list2 = new ArrayList();
                sess2.setGlobal( "list",
                                 list2 );

                sess2.execute( CommandFactory.newInsertElements(Arrays.asList(data)));
                //session.execute( data );
                count++;
            }
            System.out.println( "Iterations in for " + message + " : " + count );

        }

    }

    @Test(timeout = 10000L)
    public void testSequentialWithNoLoop() throws Exception {
        // BZ-1228098
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import \n" + Message.class.getCanonicalName() + ";" +
                "rule R1 no-loop when\n" +
                "    $s : String( )" +
                "    $m : Message( )\n" +
                "then\n" +
                "    modify($m) { setMessage($s) };\n" +
                "end\n";

        KieServices ks = KieServices.Factory.get();
        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, str);
        KieContainer kieContainer = ks.newKieContainer(kieModule.getReleaseId());
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession sequentialKsession = kbase.newStatelessKieSession();

        List result = (List) sequentialKsession.execute( CommandFactory.newInsertElements(Arrays.asList("test", new Message())));
        assertEquals( 2, result.size() );

        StatelessKieSession ksession = kieContainer.getKieBase().newStatelessKieSession();
        result = (List) ksession.execute( CommandFactory.newInsertElements(Arrays.asList("test", new Message())));
        assertEquals( 2, result.size() );
    }

    @Test
    public void testSharedSegment() throws Exception {
        // BZ-1228313
        String str =
                "package org.drools.mvel.compiler.test\n" +
                "import \n" + Message.class.getCanonicalName() + ";" +
                "rule R1 when\n" +
                "    $s : String()\n" +
                "    $m : Message()\n" +
                "    $i : Integer( this < $s.length )\n" +
                "then\n" +
                "    modify($m) { setMessage($s) };\n" +
                "end\n" +
                "\n" +
                "rule R2 when\n" +
                "    $s : String()\n" +
                "    $m : Message()\n" +
                "    $i : Integer( this > $s.length )\n" +
                "then\n" +
                "end\n";

        final KieModule kieModule = KieUtil.getKieModuleFromDrls("test", kieBaseTestConfiguration, str);
        final KieBase kbase = KieBaseUtil.newKieBaseFromKieModuleWithAdditionalOptions(kieModule, kieBaseTestConfiguration, SequentialOption.YES);
        StatelessKieSession ksession = kbase.newStatelessKieSession();

        ksession.execute( CommandFactory.newInsertElements(Arrays.asList("test", new Message(), 3, 5)));
    }
}

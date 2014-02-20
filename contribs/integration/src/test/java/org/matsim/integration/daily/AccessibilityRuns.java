package org.matsim.integration.daily;

import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.accessibility.AccessibilityControlerListenerImpl.Modes4Accessibility;
import org.matsim.contrib.accessibility.GridBasedAccessibilityControlerListenerV3;
import org.matsim.contrib.matrixbasedptrouter.PtMatrix;
import org.matsim.core.api.experimental.facilities.ActivityFacilities;
import org.matsim.core.api.experimental.facilities.ActivityFacility;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup.ActivityDurationInterpretation;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.PlanStrategyRegistrar;
import org.matsim.core.facilities.ActivityOption;
import org.matsim.core.facilities.FacilitiesUtils;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.ExeRunner;
import org.matsim.testcases.MatsimTestUtils;

public class AccessibilityRuns {
	private static final Logger log = Logger.getLogger( AccessibilityRuns.class ) ; 

	@Rule public MatsimTestUtils utils = new MatsimTestUtils() ;

	@Test
	public void doTest() {
		System.out.println("available ram: " + (Runtime.getRuntime().maxMemory() / 1024/1024));

		final String FN = "matsimExamples/tutorial/lesson-3/network.xml" ;

		Config config = ConfigUtils.createConfig();
		Scenario sc = ScenarioUtils.createScenario( config ) ;

		try {
			new MatsimNetworkReader(sc).readFile(FN);
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			new MatsimNetworkReader(sc).readFile("../" + FN);
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			new MatsimNetworkReader(sc).readFile("../../" + FN);
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			new MatsimNetworkReader(sc).readFile("../../../" + FN);
			// this is the one that works locally
		} catch (Exception e) {
			e.printStackTrace();
		}


		Assert.assertTrue(true);
	}

	@Test
	public void doAccessibilityTest() {
		Config config = ConfigUtils.createConfig() ;

		config.network().setInputFile("../../../matsimExamples/countries/za/nmbm/network/network.xml.gz" );
		config.facilities().setInputFile("../../../matsimExamples/countries/za/nmbm/facilities/facilities.xml.gz" );

		config.controler().setLastIteration(0);

		config.vspExperimental().setVspDefaultsCheckingLevel( VspExperimentalConfigGroup.ABORT );

		// some (otherwise irrelevant) settings to make the vsp check happy:
		config.timeAllocationMutator().setMutationRange(7200.);
		config.timeAllocationMutator().setAffectingDuration(false);
		config.vspExperimental().setRemovingUnneccessaryPlanAttributes(true);
		config.vspExperimental().setActivityDurationInterpretation( ActivityDurationInterpretation.tryEndTimeThenDuration.toString() );
		{
			StrategySettings stratSets = new StrategySettings( ConfigUtils.createAvailableStrategyId(config) );
			stratSets.setModuleName( PlanStrategyRegistrar.Selector.ChangeExpBeta.toString() );
			stratSets.setProbability(1.);
			config.strategy().addStrategySettings(stratSets);
		}

		Scenario scenario = ScenarioUtils.loadScenario( config ) ;
		
		
		List<String> activityTypes = new ArrayList<String>() ;
		ActivityFacilities homes = FacilitiesUtils.createActivityFacilities("homes") ;
		for ( ActivityFacility fac : scenario.getActivityFacilities().getFacilities().values()  ) {
			for ( ActivityOption option : fac.getActivityOptions().values() ) {
				// figure out all activity types:
				if ( !activityTypes.contains(option.getType()) ) {
					activityTypes.add( option.getType() ) ;
				}
				// figure out where the homes are:
				if ( option.getType().equals("h") ) {
					homes.addActivityFacility(fac);
				}
			}
		}
		
		log.warn( "found activity types: " + activityTypes ); 
		
		// yyyy there is some problem with activity types: in some algorithms, only the first letter is interpreted, in some other algorithms,
		// the whole string.  BEWARE!  This is not good software design and should be changed.  kai, feb'14

		for ( String actType : activityTypes ) {
			if ( !actType.equals("w") ) {
				log.error("skipping everything except work for debugging purposes; remove in production code. kai, feb'14") ;
				continue ;
			}
			
			config.controler().setOutputDirectory( utils.getOutputDirectory() + "/" + actType + "/" );
			ActivityFacilities opportunities = FacilitiesUtils.createActivityFacilities() ;
			for ( ActivityFacility fac : scenario.getActivityFacilities().getFacilities().values()  ) {
				for ( ActivityOption option : fac.getActivityOptions().values() ) {
					if ( option.getType().equals(actType) ) {
						opportunities.addActivityFacility(fac);
					}
				}
			}

			Controler controler = new Controler(scenario) ;
			// yy it is a bit annoying to have to run the controler separately for every act type, or even to run it at all.
			// But the computation uses too much infrastructure which is plugged together in the controler.
			// (Might be able to get away with ONE controler run if we manage to write the accessibility results
			// in subdirectories of the controler output directory.) kai, feb'14
			
			controler.setOverwriteFiles(true);

			PtMatrix ptMatrix = null  ;
			GridBasedAccessibilityControlerListenerV3 listener = 
					new GridBasedAccessibilityControlerListenerV3(opportunities, ptMatrix, config, scenario.getNetwork( ));
			listener.setComputingAccessibilityForMode( Modes4Accessibility.freeSpeed, true );
			listener.addAdditionalFacilityData( homes ) ;
			listener.generateGridsAndMeasuringPointsByNetwork(scenario.getNetwork(), 1000. );

			controler.addControlerListener(listener);

			controler.run() ;

			try {
				BufferedWriter writer = IOUtils.getBufferedWriter( config.controler().getOutputDirectory() + "/t.gpl" ) ;
				// yy might be worthwhile to find out what all the following instructions actually do; I took them from an example.
				// kai, feb'14
				writer.write("set pm3d map\n") ;
				writer.write("set pm3d flush begin\n") ;
				writer.write("set palette defined ( 0. '#ffffff', 0.5 '#ffff00', 1. '#0000ff' )\n") ;
				writer.write("set zrange [-0:10]\n") ;
				writer.write("set term pdf size 25cm,15cm \n") ;
				writer.write("set out 'accessibility.pdf'\n") ;
				writer.write("set title 'accessibility to " + actType + "'\n") ;
				writer.write("splot \"<awk '!/access/{print $0}' accessibilities.csv\" u 1:2:3\n ") ;
				// (the awk command filters out the first line)
				writer.close();
			} catch (Exception ee ) {
				ee.printStackTrace(); 
				throw new RuntimeException( "writing t.gpl did not work") ;
			}

			String cmd = "/opt/local/bin/gnuplot t.gpl" ;
			String stdoutFileName = config.controler().getOutputDirectory() + "/gnuplot.log" ;
			int timeout = 99999 ;
			String workingDirectory = config.controler().getOutputDirectory() ;
			ExeRunner.run(cmd, stdoutFileName, timeout, workingDirectory) ;

		}

	}

}
